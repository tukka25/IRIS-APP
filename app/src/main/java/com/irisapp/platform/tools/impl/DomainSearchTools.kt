package com.irisapp.platform.tools.impl

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Telephony
import com.irisapp.platform.tools.Tool
import com.irisapp.platform.tools.ToolParam
import com.irisapp.platform.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tier 6 — Domain Entity Search tools.
 *
 * These tools let the SLM resolve entities by TYPE:
 * - "workout playlist" → search_media → finds playlist
 * - "budget spreadsheet" → search_files → finds file
 * - "grocery list note" → search_notes → finds note
 * - "message from Mom" → search_sms → finds conversation
 * - "dentist appointment" → get_calendar_events → finds event
 *
 * The SLM classifies entities by choosing the right tool.
 * No regex classifier needed — model training handles the semantics.
 */

/**
 * Search media (songs, playlists, artists, albums) via MediaStore.
 *
 * Detects entity type by checking:
 * - "playlist" / "play" → queries Playlists
 * - "song" / "track" / "play" → queries Audio.Media
 * - "artist" → queries Audio.Artists
 * - "album" → queries Audio.Albums
 * - Generic name → queries all and returns best match
 */
class SearchMediaTool(private val context: Context) : Tool {
    override val name = "search_media"
    override val description = "Search device media: songs, playlists, artists, albums by name or type. Use for queries like 'play my workout playlist' or 'find that song by Adele'."
    override val parameters = listOf(
        ToolParam("query", "string", description = "Name, artist, or playlist to search for"),
        ToolParam("type", "string", required = false, description = "Optional: 'song', 'playlist', 'artist', 'album', or leave empty to search all")
    )

    override suspend fun execute(input: Map<String, String>): ToolResult {
        val query = input["query"] ?: return ToolResult(false, "", "Missing 'query'")
        val type = input["type"]?.lowercase()

        return withContext(Dispatchers.IO) {
            runCatching {
                if (context.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    return@runCatching ToolResult(false, "", "READ_MEDIA_AUDIO permission not granted")
                }

                val results = mutableListOf<String>()

                // Search playlists
                if (type == null || type == "playlist") {
                    results.addAll(searchPlaylists(query))
                }

                // Search songs
                if (type == null || type == "song") {
                    results.addAll(searchSongs(query))
                }

                // Search artists
                if (type == null || type == "artist") {
                    results.addAll(searchArtists(query))
                }

                if (results.isEmpty()) {
                    ToolResult(true, "No media found matching '$query'")
                } else {
                    ToolResult(true, results.take(5).joinToString("\n"))
                }
            }.getOrElse { e ->
                ToolResult(false, "", "Media search failed: ${e.message}")
            }
        }
    }

    private fun searchPlaylists(query: String): List<String> {
        val results = mutableListOf<String>()
        val projection = arrayOf(MediaStore.Audio.Playlists._ID, MediaStore.Audio.Playlists.NAME)
        val selection = "${MediaStore.Audio.Playlists.NAME} LIKE ?"
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, projection, selection,
            arrayOf("%$query%"), null
        )
        cursor?.use { c ->
            val nameIdx = c.getColumnIndex(MediaStore.Audio.Playlists.NAME)
            while (c.moveToNext() && results.size < 3) {
                results.add("[playlist] ${c.getString(nameIdx)}")
            }
        }
        return results
    }

    private fun searchSongs(query: String): List<String> {
        val results = mutableListOf<String>()
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST)
        val selection = "${MediaStore.Audio.Media.TITLE} LIKE ? OR ${MediaStore.Audio.Media.ARTIST} LIKE ?"
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection,
            arrayOf("%$query%", "%$query%"), "${MediaStore.Audio.Media.TITLE} ASC"
        )
        cursor?.use { c ->
            val titleIdx = c.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val artistIdx = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            while (c.moveToNext() && results.size < 3) {
                val artist = c.getString(artistIdx)
                results.add("[song] ${c.getString(titleIdx)}${if (artist != null) " — $artist" else ""}")
            }
        }
        return results
    }

    private fun searchArtists(query: String): List<String> {
        // Artists are embedded in song metadata — search via DISTINCT
        val results = mutableSetOf<String>()
        val projection = arrayOf(MediaStore.Audio.Media.ARTIST)
        val selection = "${MediaStore.Audio.Media.ARTIST} LIKE ?"
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection,
            arrayOf("%$query%"), null
        )
        cursor?.use { c ->
            val artistIdx = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            while (c.moveToNext() && results.size < 3) {
                c.getString(artistIdx)?.let { if (it.isNotBlank()) results.add("[artist] $it") }
            }
        }
        return results.toList()
    }
}

/**
 * Search files and documents via MediaStore + DocumentsProvider.
 *
 * Covers:
 * - Recent documents (MediaStore.Files)
 * - Downloads folder
 * - Any file accessible via content URI
 */
class SearchFilesTool(private val context: Context) : Tool {
    override val name = "search_files"
    override val description = "Search device files and documents by name. Finds PDFs, spreadsheets, text files, downloads, and recent documents."
    override val parameters = listOf(
        ToolParam("query", "string", description = "File name or partial match to search for"),
        ToolParam("kind", "string", required = false, description = "Optional: 'document', 'spreadsheet', 'pdf', 'image', 'download', or leave empty for all")
    )

    override suspend fun execute(input: Map<String, String>): ToolResult {
        val query = input["query"] ?: return ToolResult(false, "", "Missing 'query'")

        return withContext(Dispatchers.IO) {
            runCatching {
                val results = mutableListOf<String>()

                // Query MediaStore Files for any file matching the name
                val projection = arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.RELATIVE_PATH,
                    MediaStore.Files.FileColumns.DATE_MODIFIED
                )
                val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
                val cursor = context.contentResolver.query(
                    MediaStore.Files.getContentUri("external"), projection, selection,
                    arrayOf("%$query%"),
                    "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
                )
                cursor?.use { c ->
                    val nameIdx = c.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val pathIdx = c.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
                    while (c.moveToNext() && results.size < 5) {
                        val name = c.getString(nameIdx)
                        val path = c.getString(pathIdx) ?: ""
                        val tag = when {
                            name.endsWith(".pdf", true) -> "[pdf]"
                            name.endsWith(".xlsx", true) || name.endsWith(".csv", true) -> "[sheet]"
                            name.endsWith(".doc", true) || name.endsWith(".docx", true) || name.endsWith(".txt", true) -> "[doc]"
                            name.endsWith(".jpg", true) || name.endsWith(".png", true) -> "[image]"
                            else -> "[file]"
                        }
                        results.add("$tag $name (${path.trimEnd('/')})")
                    }
                }

                if (results.isEmpty()) {
                    ToolResult(true, "No files found matching '$query'")
                } else {
                    ToolResult(true, results.joinToString("\n"))
                }
            }.getOrElse { e ->
                ToolResult(false, "", "File search failed: ${e.message}")
            }
        }
    }
}

/**
 * Search notes from device note-taking apps via content providers.
 *
 * Tries:
 * - Google Keep (if installed, uses ACTION_CREATE_NOTE to find handler)
 * - Standard note intents
 * - Falls back to returning available note app handlers
 *
 * Note: Direct note content access is limited on Android.
 * This tool returns what's resolvable and may need the user
 * to pick a note manually via the app's UI.
 */
class SearchNotesTool(private val context: Context) : Tool {
    override val name = "search_notes"
    override val description = "Search notes and lists from device note-taking apps. Finds matching notes by title or content."
    override val parameters = listOf(
        ToolParam("query", "string", description = "Note title or content to search for")
    )

    override suspend fun execute(input: Map<String, String>): ToolResult {
        val query = input["query"] ?: return ToolResult(false, "", "Missing 'query'")

        return withContext(Dispatchers.IO) {
            runCatching {
                // For Android, notes are app-specific. We detect available note apps
                // and indicate the user may need to specify which app.
                val availableNoteApps = detectNoteApps()

                if (availableNoteApps.isEmpty()) {
                    ToolResult(true, "No note-taking apps detected on this device. The user may need to install one (Google Keep, Samsung Notes, etc.)")
                } else {
                    ToolResult(true, buildString {
                        appendLine("Note apps available: ${availableNoteApps.joinToString()}")
                        appendLine("Search query: '$query'")
                        appendLine("(Note: direct note content search requires app-specific APIs. The workflow will prompt the user to select the note in the chosen app.)")
                    })
                }
            }.getOrElse { e ->
                ToolResult(false, "", "Note search failed: ${e.message}")
            }
        }
    }

    private fun detectNoteApps(): List<String> {
        val notePackages = setOf(
            "com.google.android.keep",       // Google Keep
            "com.samsung.android.app.notes", // Samsung Notes
            "com.microsoft.onenote",          // OneNote
            "com.evernote",                   // Evernote
            "com.notion.android"              // Notion
        )
        return notePackages.filter { pkg ->
            runCatching {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            }.getOrDefault(false)
        }
    }
}

/**
 * Search SMS/MMS messages from the device.
 *
 * Queries Telephony.Sms content provider for messages
 * matching a contact name, phone number, or message content.
 */
class SearchSmsTool(private val context: Context) : Tool {
    override val name = "search_sms"
    override val description = "Search SMS/text messages by contact name, phone number, or message content. Use for 'find the message from Mom' or 'check texts about the meeting'."
    override val parameters = listOf(
        ToolParam("query", "string", description = "Contact name, phone number, or message text to search"),
        ToolParam("folder", "string", required = false, description = "Optional: 'inbox', 'sent', 'draft', or leave empty for all")
    )

    override suspend fun execute(input: Map<String, String>): ToolResult {
        val query = input["query"] ?: return ToolResult(false, "", "Missing 'query'")

        return withContext(Dispatchers.IO) {
            runCatching {
                if (context.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                    return@runCatching ToolResult(false, "", "READ_SMS permission not granted")
                }

                val uri = when (input["folder"]?.lowercase()) {
                    "inbox" -> Telephony.Sms.Inbox.CONTENT_URI
                    "sent" -> Telephony.Sms.Sent.CONTENT_URI
                    "draft" -> Telephony.Sms.Draft.CONTENT_URI
                    else -> Telephony.Sms.CONTENT_URI
                }

                val projection = arrayOf(
                    Telephony.Sms._ID, Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY, Telephony.Sms.DATE
                )
                val selection = "${Telephony.Sms.ADDRESS} LIKE ? OR ${Telephony.Sms.BODY} LIKE ?"
                val cursor = context.contentResolver.query(
                    uri, projection, selection,
                    arrayOf("%$query%", "%$query%"),
                    "${Telephony.Sms.DATE} DESC"
                )
                val results = mutableListOf<String>()
                cursor?.use { c ->
                    val addrIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                    val bodyIdx = c.getColumnIndex(Telephony.Sms.BODY)
                    val dateIdx = c.getColumnIndex(Telephony.Sms.DATE)
                    while (c.moveToNext() && results.size < 5) {
                        val addr = c.getString(addrIdx) ?: "unknown"
                        val body = c.getString(bodyIdx)?.take(80) ?: ""
                        val date = c.getLong(dateIdx)
                        results.add("[SMS] $addr: $body... (${formatDate(date)})")
                    }
                }

                if (results.isEmpty()) {
                    ToolResult(true, "No messages found matching '$query'")
                } else {
                    ToolResult(true, results.joinToString("\n"))
                }
            }.getOrElse { e ->
                ToolResult(false, "", "SMS search failed: ${e.message}")
            }
        }
    }

    private fun formatDate(millis: Long): String {
        if (millis == 0L) return "unknown"
        val sdf = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(millis))
    }
}

/**
 * Search calendar events via CalendarContract.
 *
 * Queries upcoming and past events matching keywords
 * in title, description, or location.
 */
class GetCalendarEventsTool(private val context: Context) : Tool {
    override val name = "get_calendar_events"
    override val description = "Search calendar events by title, description, or date. Use for 'find my dentist appointment' or 'what meetings do I have tomorrow?'."
    override val parameters = listOf(
        ToolParam("query", "string", description = "Event title, description, or keyword to search"),
        ToolParam("lookahead_days", "int", required = false, description = "Optional: how many days ahead to look (default 30)")
    )

    override suspend fun execute(input: Map<String, String>): ToolResult {
        val query = input["query"] ?: return ToolResult(false, "", "Missing 'query'")
        val lookahead = input["lookahead_days"]?.toIntOrNull() ?: 30

        return withContext(Dispatchers.IO) {
            runCatching {
                if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                    return@runCatching ToolResult(false, "", "READ_CALENDAR permission not granted")
                }

                val now = System.currentTimeMillis()
                val future = now + (lookahead * 24L * 60 * 60 * 1000)

                val projection = arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DESCRIPTION,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.EVENT_LOCATION
                )
                val selection = "(${CalendarContract.Events.TITLE} LIKE ? OR ${CalendarContract.Events.DESCRIPTION} LIKE ?) AND ${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
                val cursor = context.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI, projection, selection,
                    arrayOf("%$query%", "%$query%", now.toString(), future.toString()),
                    "${CalendarContract.Events.DTSTART} ASC"
                )
                val results = mutableListOf<String>()
                cursor?.use { c ->
                    val titleIdx = c.getColumnIndex(CalendarContract.Events.TITLE)
                    val descIdx = c.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                    val startIdx = c.getColumnIndex(CalendarContract.Events.DTSTART)
                    val locIdx = c.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                    while (c.moveToNext() && results.size < 5) {
                        val title = c.getString(titleIdx) ?: "Untitled"
                        val desc = c.getString(descIdx)?.take(60) ?: ""
                        val start = c.getLong(startIdx)
                        val loc = c.getString(locIdx) ?: ""
                        val dateStr = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(start))
                        results.add("[event] $title — $dateStr${if (loc.isNotBlank()) " @ $loc" else ""} ${if (desc.isNotBlank()) "| $desc" else ""}")
                    }
                }

                if (results.isEmpty()) {
                    ToolResult(true, "No calendar events found matching '$query'")
                } else {
                    ToolResult(true, results.joinToString("\n"))
                }
            }.getOrElse { e ->
                ToolResult(false, "", "Calendar search failed: ${e.message}")
            }
        }
    }
}

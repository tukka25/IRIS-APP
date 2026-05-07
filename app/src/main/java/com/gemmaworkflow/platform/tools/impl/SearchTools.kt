package com.gemmaworkflow.platform.tools.impl

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import com.gemmaworkflow.platform.tools.Tool
import com.gemmaworkflow.platform.tools.ToolParam
import com.gemmaworkflow.platform.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Tier 3 — Search & Knowledge tools.
 * web_search: self-hosted or free search API
 * search_places: OpenStreetMap Nominatim (free, no API key)
 * lookup_contact: Android ContactsContract content provider
 */

/** Minimal web search using DuckDuckGo Lite (no API key needed). */
object WebSearchTool : Tool {
    override val name = "web_search"
    override val description = "Search the web, returns top 3 results"
    override val parameters = listOf(
        ToolParam("query", "string", description = "Search query")
    )

    override suspend fun execute(input: Map<String, String>): ToolResult {
        val query = input["query"] ?: return ToolResult(false, "", "Missing 'query'")
        return withContext(Dispatchers.IO) {
            runCatching {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = URL("https://lite.duckduckgo.com/lite/?q=$encoded")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "GemmaWorkflow/1.0")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val results = extractResults(body, query).take(3)

                if (results.isEmpty()) {
                    ToolResult(true, "No results found for '$query'")
                } else {
                    ToolResult(true, results.joinToString("\n"))
                }
            }.getOrElse { e ->
                ToolResult(false, "", "Search failed: ${e.message}")
            }
        }
    }

    private fun extractResults(html: String, query: String): List<String> {
        // Extract result snippets from DuckDuckGo Lite
        val snippets = Regex("""<td class="result-snippet">(.*?)</td>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(html).map { it.groupValues[1].replace(Regex("<[^>]+>"), "").trim() }.toList()

        val links = Regex("""<a rel="nofollow" href="([^"]+)""")
            .findAll(html).map { it.groupValues[1] }.toList()

        return snippets.zip(links).mapIndexed { i, (snippet, link) ->
            "${i + 1}. ${snippet.take(120)}... ($link)"
        }.ifEmpty {
            // Fallback: try extracting links only
            Regex("""<a[^>]*class="result-link"[^>]*href="([^"]+)""")
                .findAll(html).map { it.groupValues[1] }.take(3)
                .mapIndexed { i, link -> "${i + 1}. $link" }.toList()
        }
    }
}

/** Search places using OpenStreetMap Nominatim — free, no API key. */
object SearchPlacesTool : Tool {
    override val name = "search_places"
    override val description = "Search places/addresses, returns top 5 matches with lat/lng"
    override val parameters = listOf(
        ToolParam("query", "string", description = "Place name, address, or category"),
        ToolParam("near", "string", required = false, description = "Optional: city or area to bias results")
    )

    override suspend fun execute(input: Map<String, String>): ToolResult {
        val query = input["query"] ?: return ToolResult(false, "", "Missing 'query'")
        return withContext(Dispatchers.IO) {
            runCatching {
                var url = "https://nominatim.openstreetmap.org/search" +
                    "?q=${URLEncoder.encode(query, "UTF-8")}" +
                    "&format=json&limit=5&addressdetails=1"

                input["near"]?.let { url += "&q=${URLEncoder.encode("$query $it", "UTF-8")}" }

                val conn = URL(url).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "GemmaWorkflow/1.0 (Android automation app)")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val results = parseNominatim(body)

                if (results.isEmpty()) {
                    ToolResult(true, "No places found for '$query'")
                } else {
                    ToolResult(true, results.joinToString("\n"))
                }
            }.getOrElse { e ->
                ToolResult(false, "", "Place search failed: ${e.message}")
            }
        }
    }

    private fun parseNominatim(json: String): List<String> {
        val items = Regex("""\{[^}]+}""").findAll(json).toList()
        return items.take(5).map { item ->
            val name = Regex(""""display_name"\s*:\s*"([^"]+)"""").find(item.value)?.groupValues?.get(1) ?: ""
            val lat = Regex(""""lat"\s*:\s*"([^"]+)"""").find(item.value)?.groupValues?.get(1) ?: ""
            val lng = Regex(""""lon"\s*:\s*"([^"]+)"""").find(item.value)?.groupValues?.get(1) ?: ""
            "${name.take(60)} ($lat, $lng)"
        }
    }
}

/** Look up a contact from the device address book. */
class LookupContactTool(private val context: Context) : Tool {
    override val name = "lookup_contact"
    override val description = "Search device contacts by name, returns phone + email"
    override val parameters = listOf(
        ToolParam("name", "string", description = "Contact name or partial match")
    )

    override suspend fun execute(input: Map<String, String>): ToolResult {
        val name = input["name"] ?: return ToolResult(false, "", "Missing 'name'")

        return runCatching {
            val results = mutableListOf<String>()

            // Query contacts by display name
            val cursor: Cursor? = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
                "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                null
            )

            cursor?.use { c ->
                val nameIdx = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val idIdx = c.getColumnIndex(ContactsContract.Contacts._ID)

                while (c.moveToNext() && results.size < 5) {
                    val contactId = c.getString(idIdx)
                    val displayName = c.getString(nameIdx)
                    val phones = getPhones(contactId)
                    val emails = getEmails(contactId)
                    results.add("$displayName | phone: ${phones.ifEmpty { "none" }} | email: ${emails.ifEmpty { "none" }}")
                }
            }

            ToolResult(true, results.joinToString("\n").ifBlank { "No contacts matching '$name'" })
        }.getOrElse { e ->
            ToolResult(false, "", "Contact lookup failed: ${e.message}")
        }
    }

    private fun getPhones(contactId: String): List<String> {
        val phones = mutableListOf<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId), null
        )?.use { c ->
            val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (c.moveToNext()) phones.add(c.getString(numIdx))
        }
        return phones
    }

    private fun getEmails(contactId: String): List<String> {
        val emails = mutableListOf<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactId), null
        )?.use { c ->
            val addrIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            while (c.moveToNext()) emails.add(c.getString(addrIdx))
        }
        return emails
    }
}

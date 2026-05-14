package com.irisapp.platform.tools.impl

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.irisapp.platform.tools.Tool
import com.irisapp.platform.tools.ToolParam
import com.irisapp.platform.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Clipboard tools.
 *
 * Android 10+ limits clipboard reads unless the app is in the foreground
 * or is the current input method editor (IME). These tools work when
 * IrisApp is the active foreground app.
 *
 * For the write tool: copies text to the system clipboard for use
 * by other apps (e.g. "copy this and open WhatsApp").
 */
class GetClipboardTextTool(private val context: Context) : Tool {
    override val name = "get_clipboard_text"
    override val description = "Reads text from the system clipboard. Only works when IrisApp is in the foreground (Android 10+ restriction)."
    override val parameters = emptyList<ToolParam>()

    override suspend fun execute(input: Map<String, String>): ToolResult {
        return withContext(Dispatchers.Main) {
            runCatching {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = cm.primaryClip
                if (clip == null || clip.itemCount == 0) {
                    return@runCatching ToolResult(true, "Clipboard is empty")
                }
                val text = clip.getItemAt(0).text?.toString() ?: ""
                if (text.isBlank()) {
                    ToolResult(true, "Clipboard item is not text")
                } else {
                    ToolResult(true, "Clipboard text: ${text.take(500)}")
                }
            }.getOrElse { e ->
                ToolResult(false, "", "Clipboard read failed: ${e.message}. May need foreground access (Android 10+).")
            }
        }
    }
}

class SetClipboardTextTool(private val context: Context) : Tool {
    override val name = "set_clipboard_text"
    override val description = "Copies text to the system clipboard. Use for 'copy this message' or 'put this in my clipboard'."
    override val parameters = listOf(
        ToolParam("text", "string", description = "Text to copy to clipboard")
    )

    override suspend fun execute(input: Map<String, String>): ToolResult {
        val text = input["text"] ?: return ToolResult(false, "", "Missing 'text' param")

        return withContext(Dispatchers.Main) {
            runCatching {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = android.content.ClipData.newPlainText("IrisApp", text)
                cm.setPrimaryClip(clip)
                ToolResult(true, "Copied to clipboard: ${text.take(100)}")
            }.getOrElse { e ->
                ToolResult(false, "", "Clipboard write failed: ${e.message}")
            }
        }
    }
}

class OpenDeepLinkTool(private val context: Context) : Tool {
    override val name = "open_deep_link"
    override val description = "Opens a URL or deep link in the appropriate app (browser, YouTube, Maps, etc.). Resolves the handler before launching."
    override val parameters = listOf(
        ToolParam("url", "string", description = "URL or deep link to open (https://, geo://, youtube://, etc.)")
    )

    override suspend fun execute(input: Map<String, String>): ToolResult {
        val url = input["url"] ?: return ToolResult(false, "", "Missing 'url' param")

        return withContext(Dispatchers.IO) {
            runCatching {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse(url)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                // Check if any app can handle this
                val resolved = if (Build.VERSION.SDK_INT >= 33) {
                    context.packageManager.resolveActivity(
                        intent,
                        android.content.pm.PackageManager.ResolveInfoFlags.of(
                            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY.toLong()
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                }

                if (resolved == null) {
                    return@runCatching ToolResult(false, "", "No app found to handle: $url")
                }

                context.startActivity(intent)
                ToolResult(true, "Opened: $url via ${resolved.activityInfo.packageName}")
            }.getOrElse { e ->
                ToolResult(false, "", "Deep link failed: ${e.message}")
            }
        }
    }
}

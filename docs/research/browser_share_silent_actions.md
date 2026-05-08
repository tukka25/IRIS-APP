# Browser and Share Actions — Silent vs. UI-Full Analysis

For gemma4good_android Milestone 6 (workflow lifecycle, store, run, trigger, execute).

---

## 1. Browser: Chrome Custom Tabs

### What it is
Chrome Custom Tabs lets an app open web content in a Chrome-styled tab that
lives inside the app's window. It is NOT a WebView — it is a real Chrome
rendering surface managed by the Chrome APK, but presented inside your app's
task with a toolbar you control.

### Key behavior
- **Stays within app?** YES. The Custom Tab is a surfaced activity that animates
  in over your app. The user sees a Chrome-styled bar (URL, close button,
  toolbar color) but the back button returns to the calling app, not to Chrome.
- **Renders visually?** YES. The full Chrome rendering engine is used.
- **Avoids app switching?** YES, no task switch to a separate Chrome icon.
- **Still has UI chrome** (address bar, toolbar, close button). It is NOT silent.
- **No separate APK launch** — Chrome process is reused via service binding.

### API surface

```kotlin
// Dependency: androidx.browser:browser:1.8.0
// (already on compose-bom transitively via activity-compose)

// 1. Bind to Chrome (optional but recommended for warmup)
val customTabsClient = CustomTabsClient.bindCustomTabsService(
    context,
    "com.android.chrome"   // Chrome package
)
val session = customTabsClient?.newSession(null)

// 2. Build the intent
val customTabsIntent = CustomTabsIntent.Builder(session)
    .setUrlBarHidingEnabled(true)           // hide URL bar on scroll
    .setShowTitle(true)
    .setToolbarColor(0xFF4285F4.toInt())    // brand color
    .setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
    .setShareState(CustomTabsIntent.SHARE_STATE_ON)  // show share button
    .build()

// 3. Launch (stays in-app)
customTabsIntent.launchUrl(context, Uri.parse("https://example.com"))
```

### AndroidX Browser Library — Full Pattern

```kotlin
// Full implementation with Chrome service connection
class ChromeCustomTabOpener(private val context: Context) {

    private var customTabsClient: CustomTabsClient? = null
    private var customTabsSession: CustomTabsSession? = null

    fun bindService(onReady: () -> Unit) {
        CustomTabsClient.bindCustomTabsService(
            context,
            "com.android.chrome",
            object : CustomTabsServiceConnection() {
                override fun onServiceConnected(name: ComponentName, client: CustomTabsClient) {
                    client.warmup(0L)  // pre-load Chrome
                    customTabsClient = client
                    customTabsSession = client.newSession(null)
                    onReady()
                }
                override fun onServiceDisconnected(name: ComponentName) {
                    customTabsClient = null
                    customTabsSession = null
                }
            }
        )
    }

    fun openUrl(url: String, toolbarColor: Int? = null) {
        val builder = CustomTabsIntent.Builder(customTabsSession)
        builder.setShowTitle(true)
        toolbarColor?.let { builder.setToolbarColor(it) }
        val intent = builder.build()
        intent.launchUrl(context, Uri.parse(url))
    }
}
```

### Fallback if Chrome not available

1. **Catch `ActivityNotFoundException`** and fall back to standard
   `Intent.ACTION_VIEW` with a URL. The system will show a chooser of
   any browsers that are installed.

```kotlin
try {
    chromeCustomTabOpener.openUrl(url)
} catch (_: ActivityNotFoundException) {
    // Fallback: standard Intent.ACTION_VIEW (shows chooser or opens default browser)
    val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(fallbackIntent)
}
```

2. **Detection approach** — query PackageManager for `com.android.chrome`
   before binding. If absent, skip Custom Tabs entirely and go straight to
   `ACTION_VIEW`.

```kotlin
fun isChromeAvailable(context: Context): Boolean {
    return context.packageManager.resolveActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse("https://")).apply {
            setPackage("com.android.chrome")
        },
        PackageManager.MATCH_DEFAULT_ONLY
    ) != null
}
```

### Summary — Chrome Custom Tabs

| Aspect | Value |
|--------|-------|
| App switching | No — stays in-app (but Chrome process) |
| Visual rendering | Yes — full Chrome engine |
| Silent | No — URL bar + toolbar always visible |
| Min Chrome version | Chrome 45+ (Android 5.0+), 95%+ of devices |
| Fallback | `Intent.ACTION_VIEW` (standard browser chooser) |

---

## 2. Share Text Silently — ClipboardManager + ClipData

### What it is
Directly write text to the system clipboard using `ClipData`. No chooser,
no share sheet, no app switching. Just writes to clipboard and optionally
shows a toast. The user manually pastes elsewhere.

### Permissions
**None required.** `ClipboardManager` access does not need any runtime or
manifest permission on Android 10+ (API 29+). Even on older APIs,
`WRITE_EXTERNAL_STORAGE` is not needed for clipboard.

### Code pattern

```kotlin
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

fun copyTextToClipboardSilently(context: Context, text: String, label: String = "GemmaWorkflow") {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}
```

### UX Trade-off — MUST be documented

> **Limitation: User must paste manually.**
> Copying to clipboard is a silent, zero-UI operation (except optional toast).
> The text lands in the system clipboard. The user must switch to the target
> app and paste. There is no way to silently inject text into another app
> without accessibility / automation permissions (which require extra setup
> and are restricted on Play Store distribution).
>
> This is a deliberate UX trade-off: **clipboard copy = guaranteed silent,
> zero-UI, no permission blast, no Play Policy violation** vs.
> **share sheet = full UI chooser but direct handoff**.

### Current app status
The existing `share.share_text` action uses `Intent.ACTION_SEND` which
**always shows the share sheet chooser** — it is not silent.
A new action `clipboard.copy_text` should be added to `ActionSpecRegistry`
for truly silent clipboard-only sharing.

---

## 3. Share Image Silently — ClipboardManager for Image URI

### What it is
Same pattern as text. Write a content URI pointing to an image to the clipboard
using `ClipData.newUri()`. No chooser, no share sheet.

### Required permissions to READ the image

If the image is a `content://` URI from `MediaStore` or a file path:

- **No runtime permission needed** if you already hold the URI (e.g., you
  generated it or received it from `MediaStore`).
- **If reading a file path** (`file://`) on Android 10+ (API 29+),
  `READ_EXTERNAL_STORAGE` or `READ_MEDIA_IMAGES` may be needed depending on
  scoped storage enforcement.
- **`content://` URIs from MediaStore** (e.g., `content://media/external/images/media/1`)
  generally do not need additional permissions if accessed immediately after
  obtaining the URI from the MediaStore query.

### Code pattern

```kotlin
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast

fun copyImageUriToClipboardSilently(context: Context, imageUri: Uri, label: String = "GemmaWorkflow") {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    // FLAG_GRANT_READ_URI_PERMISSION lets the clipboard own a temporary read grant
    val clip = ClipData.newUri(context.contentResolver, label, imageUri)
    clip.addItem(ClipData.Item(imageUri))
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Image copied to clipboard", Toast.LENGTH_SHORT).show()
}
```

### Permissions for MediaStore image access

If the app is querying MediaStore to GET the image URI before copying:

| Android version | Permission needed for MediaStore query |
|----------------|----------------------------------------|
| API 33+ (Android 13+) | `READ_MEDIA_IMAGES` (runtime permission) |
| API 29–32 | `READ_EXTERNAL_STORAGE` (runtime permission) |
| API < 29 | `READ_EXTERNAL_STORAGE` (install time) |

If the app generates the image (e.g., saves a generated bitmap to its own
app-specific directory), no permission is needed to copy its `FileProvider` URI
to the clipboard.

---

## 4. Existing App Patterns vs. Silent Alternatives

### Current `browser.open_url` — NOT silent, uses chooser
```kotlin
// ActionSpecRegistry — uses Intent.ACTION_VIEW with no package
execution = ExecutionSpec.AndroidIntent(
    action = Intent.ACTION_VIEW,
    dataTemplate = "{url}"
    // No setPackage → shows system chooser
)
```

### Proposed `browser.chrome_custom_tab` — NOT silent, but no app switch
```kotlin
// New ActionSpec entry in ActionSpecRegistry:
id = "browser.chrome_custom_tab"
execution = ExecutionSpec.CustomTab(   // new ExecutionSpec variant
    url = "{url}",
    toolbarColor = null  // optional brand color
)
// WorkflowRunner dispatches to ChromeCustomTabOpener
```

### Proposed `clipboard.copy_text` — fully silent
```kotlin
// New ActionSpec entry:
id = "clipboard.copy_text"
execution = ExecutionSpec.ClipboardText(paramName = "text")
// WorkflowRunner dispatches to ClipboardManager directly
// No Intent involved, no chooser, no app switching
```

---

## 5. Summary Table

| Action | UI | App Switch | Silent | Permission | Fallback |
|--------|----|-----------|--------|------------|----------|
| `browser.open_url` (current) | Chooser dialog | Yes → browser | No | None | None (falls back to available browser) |
| Chrome Custom Tab | Chrome-styled tab | No (in-app) | No (toolbar visible) | None | `Intent.ACTION_VIEW` |
| `clipboard.copy_text` (new) | Toast only | No | **Yes** | None | None (paste manual) |
| `share.share_text` (current) | Share sheet | Yes → target app | No | None | None |
| `clipboard.copy_image` (new) | Toast only | No | **Yes** | `READ_MEDIA_IMAGES` if querying MediaStore | None |

---

## 6. Implementation Notes for gemma4good_android

### Adding Chrome Custom Tab support

1. Add dependency (not yet in `app/build.gradle.kts`):
```kotlin
implementation("androidx.browser:browser:1.8.0")
```

2. Add new `ExecutionSpec.CustomTab` variant in `ActionSpecRegistry.kt`.

3. Add `ChromeCustomTabOpener` utility class in `platform/capability/`.

4. Add `tryCustomTab` branch in `WorkflowRunner.executeStep()` that catches
   `ActivityNotFoundException` and falls back to `Intent.ACTION_VIEW`.

### Adding clipboard actions

1. Add `ExecutionSpec.ClipboardText(paramName: String)` to `ExecutionSpec`
   in `ActionSpecRegistry.kt`.

2. Add `ClipboardManager.copyText()` / `ClipboardManager.copyImageUri()`
   helpers in a new `platform/capability/ClipboardHelper.kt`.

3. Add `tryClipboard` branch in `WorkflowRunner.executeStep()` that uses
   `ClipboardManager` directly (bypasses `IntentFactory` entirely).

4. Add `clipboard.copy_text` and `clipboard.copy_image` to
   `ActionSpecRegistry.all` with appropriate params.

5. Update `IntentDiscoveryEngine` to include these in the SLM prompt
   summary so the model can pick them.

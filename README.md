# GemmaWorkflow — Android Implementation Path

> **Platform:** Android (Kotlin + Jetpack Compose + JNI/NDK)
> **Goal:** On-device SLM agent that turns natural language into executable cross-app workflows with programmatic trigger creation.

---

## 1. Why Android

Apple does not allow third-party apps to:
- Create Shortcuts automations programmatically
- Import `.shortcut` files without cryptographic signing (no public API)
- Invoke other apps' App Intents directly

Android removes all of these constraints. We can build the dream UX: user types a sentence, the phone creates the automation and executes it across apps.

---

## 2. Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                         GemmaWorkflow App                             │
│                                                                        │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────────────────┐  │
│  │   Compose   │    │   Compose   │    │      Compose Screen      │  │
│  │   Home      │    │  Workflow   │    │    Trigger Builder       │  │
│  │  Screen     │    │   Library   │    │   (NFC / Time / Loc)     │  │
│  └──────┬──────┘    └──────┬──────┘    └───────────┬─────────────┘  │
│         │                  │                       │                 │
│         └──────────────────┴───────────┬───────────┘                 │
│                                        ▼                             │
│                          ┌─────────────────────────┐                 │
│                          │   WorkflowViewModel     │                 │
│                          │  (StateFlow + MVI)      │                 │
│                          └───────────┬─────────────┘                 │
│                                      │                               │
│                    ┌─────────────────┼─────────────────┐             │
│                    ▼                 ▼                 ▼             │
│          ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│          │   SLMEngine   │  │WorkflowStore │  │  TriggerEngine│      │
│          │  (llama.cpp)  │  │  (Room DB)   │  │  (Tasker API) │      │
│          └──────┬────────┘  └──────────────┘  └──────┬───────┘      │
│                 │                                      │              │
│                 ▼                                      ▼              │
│          ┌──────────────┐                    ┌──────────────┐        │
│          │  JNI Bridge  │                    │   Tasker     │        │
│          │  (C++ layer) │                    │   Plugin     │        │
│          └──────┬───────┘                    │   Broadcast  │        │
│                 │                            └──────────────┘        │
│                 ▼                                                     │
│          ┌──────────────┐                                             │
│          │  llama.cpp   │                                             │
│          │  + GGUF      │                                             │
│          └──────────────┘                                             │
│                                                                        │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │                      WorkflowRunner                              │ │
│  │  Receives PlannedWorkflow → dispatches actions:                  │ │
│  │    • explicit Intent   (Spotify, Obsidian, Things...)            │ │
│  │    • URL scheme        (fallback for apps without Intents)       │ │
│  │    • Termux script     (arbitrary Python/Node/bash)              │ │
│  │    • AccessibilityService (UI automation fallback)                │ │
│  └─────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 3. Tech Stack

| Layer | Technology | Notes |
|-------|-----------|-------|
| Language | Kotlin | Coroutines + Flow for async |
| UI | Jetpack Compose | Material 3, dark theme |
| Architecture | MVVM + MVI | ViewModel + StateFlow + sealed classes |
| DI | Hilt | or manual DI for hackathon speed |
| Persistence | Room | Workflows, history, settings |
| Preferences | DataStore | Simple key-value |
| Native | JNI + NDK | llama.cpp compiled as Android native lib |
| Build | Gradle + CMake | `externalNativeBuild` for C++ |
| Automation | Tasker plugin API | Primary trigger system |
| Fallback triggers | AlarmManager + Geofence API + NFC foreground dispatch | If Tasker not installed |
| Cross-app | Android Intents + URL schemes | Universal, documented |
| UI automation | AccessibilityService | Fallback for apps without Intents/URLs |
| Scripting | Termux intent API | Run Python/Node/bash scripts |
| NFC | `NfcAdapter` + `PendingIntent` | Read/write NDEF tags |

---

## 4. Module / Package Structure

```
app/
├── src/main/
│   ├── java/com/gemmaworkflow/
│   │   ├── GemmaWorkflowApp.kt              # Application class, DI setup
│   │   ├── di/
│   │   │   └── AppModule.kt                 # Hilt modules (or manual providers)
│   │   ├── ui/
│   │   │   ├── theme/
│   │   │   │   ├── Color.kt
│   │   │   │   ├── Theme.kt
│   │   │   │   └── Type.kt
│   │   │   ├── home/
│   │   │   │   ├── HomeScreen.kt            # Main screen: prompt input + run
│   │   │   │   └── HomeViewModel.kt         # Orchestrates plan + execute
│   │   │   ├── workflow/
│   │   │   │   ├── WorkflowListScreen.kt    # Saved workflows grid/list
│   │   │   │   └── WorkflowDetailScreen.kt  # View/edit a workflow
│   │   │   ├── trigger/
│   │   │   │   └── TriggerSetupScreen.kt    # NFC write / time / location config
│   │   │   └── components/
│   │   │       ├── PromptInput.kt           # TextField with send button
│   │   │       ├── WorkflowCard.kt          # Card showing workflow name + actions count
│   │   │       ├── JsonPreview.kt           # Monospace JSON preview of SLM output
│   │   │       └── ActionRow.kt             # Row showing single action (icon + label)
│   │   ├── data/
│   │   │   ├── local/
│   │   │   │   ├── AppDatabase.kt           # Room database
│   │   │   │   ├── WorkflowDao.kt           # CRUD for workflows
│   │   │   │   └── HistoryDao.kt            # Execution history
│   │   │   ├── model/
│   │   │   │   ├── Workflow.kt              # @Entity: id, name, trigger, actions JSON
│   │   │   │   ├── WorkflowStep.kt          # Sealed class: IntentStep, UrlStep, ScriptStep
│   │   │   │   ├── TriggerConfig.kt         # Sealed: NfcTrigger, TimeTrigger, LocationTrigger
│   │   │   │   └── ExecutionResult.kt       # Success/Failure with message
│   │   │   └── repository/
│   │   │       └── WorkflowRepository.kt    # Business logic layer
│   │   ├── domain/
│   │   │   ├── slm/
│   │   │   │   ├── SLMEngine.kt             # Kotlin interface for inference
│   │   │   │   ├── LlamaCppEngine.kt        # JNI wrapper implementation
│   │   │   │   └── PromptBuilder.kt         # System prompt with JSON schema + app catalog
│   │   │   ├── runner/
│   │   │   │   ├── WorkflowRunner.kt        # Executes WorkflowStep[] sequentially
│   │   │   │   ├── IntentDispatcher.kt      # startActivity/startService with extras
│   │   │   │   ├── UrlDispatcher.kt         # startActivity(Intent.ACTION_VIEW, uri)
│   │   │   │   ├── TermuxDispatcher.kt      # Send intent to Termux app
│   │   │   │   └── AccessibilityDispatcher.kt # Inject taps, text, gestures
│   │   │   ├── parser/
│   │   │   │   └── WorkflowJsonParser.kt    # Extract + validate JSON from raw SLM output
│   │   │   └── router/
│   │   │       └── SafeActionRouter.kt      # Validate actions against allowlist
│   │   └── automation/
│   │       ├── tasker/
│   │       │   ├── TaskerPluginHelper.kt    # Builds + sends Tasker plugin intents
│   │       │   └── TaskerConstants.kt       # Action strings, extra keys
│   │       ├── native/
│   │       │   ├── NfcHelper.kt             # Write NDEF records, register foreground dispatch
│   │       │   ├── AlarmTriggerHelper.kt    # AlarmManager for time triggers
│   │       │   └── GeofenceHelper.kt        # GeofencingClient for location triggers
│   │       └── service/
│   │           └── WorkflowAccessibilityService.kt # AccessibilityService for UI automation
│   └── cpp/
│       ├── llama-android.cpp                # JNI bridge: load model, tokenize, generate
│       ├── llama-android.h                  # JNI function declarations
│       └── CMakeLists.txt                   # Builds llama.cpp + bridge as native lib
│   └── res/
│       └── raw/
│           └── gemma-planner-dev.Q4_K_M.gguf   # Bundled model (add to .gitignore if large)
│       └── assets/
│           └── planner-json.gbnf            # Grammar file for constrained JSON
├── build.gradle.kts                         # App-level Gradle config
└── CMakeLists.txt (or in src/main/cpp)      # Native build config
```

---

## 5. LLM Inference Layer (Critical Path)

### 5.1 JNI Bridge Contract

```cpp
// llama-android.h
extern "C" {
    JNIEXPORT jlong JNICALL
    Java_com_gemmaworkflow_domain_slm_LlamaCppEngine_nativeInit(
        JNIEnv* env, jobject thiz,
        jstring modelPath, jstring grammarPath,
        jint contextSize, jint maxTokens, jint gpuLayers);

    JNIEXPORT jstring JNICALL
    Java_com_gemmaworkflow_domain_slm_LlamaCppEngine_nativeGenerate(
        JNIEnv* env, jobject thiz, jlong contextPtr, jstring prompt);

    JNIEXPORT void JNICALL
    Java_com_gemmaworkflow_domain_slm_LlamaCppEngine_nativeFree(
        JNIEnv* env, jobject thiz, jlong contextPtr);
}
```

### 5.2 Kotlin Wrapper

```kotlin
class LlamaCppEngine(private val context: Context) : SLMEngine {
    private var nativeHandle: Long = 0

    fun loadModel(config: ModelConfig) {
        val modelPath = copyAssetToCache("gemma-planner-dev.Q4_K_M.gguf")
        val grammarPath = copyAssetToCache("planner-json.gbnf")
        nativeHandle = nativeInit(modelPath, grammarPath, config.contextSize, config.maxTokens, config.gpuLayers)
    }

    override suspend fun generate(prompt: String): String = withContext(Dispatchers.Default) {
        nativeGenerate(nativeHandle, prompt)
    }

    fun unload() {
        if (nativeHandle != 0L) {
            nativeFree(nativeHandle)
            nativeHandle = 0
        }
    }

    // JNI declarations
    private external fun nativeInit(modelPath: String, grammarPath: String, contextSize: Int, maxTokens: Int, gpuLayers: Int): Long
    private external fun nativeGenerate(handle: Long, prompt: String): String
    private external fun nativeFree(handle: Long)

    companion object {
        init { System.loadLibrary("llama-android") }
    }
}
```

### 5.3 CMake Setup

```cmake
# src/main/cpp/CMakeLists.txt
cmake_minimum_required(VERSION 3.22)
project(llama-android)

set(LLAMA_DIR "${CMAKE_SOURCE_DIR}/../../../../llama.cpp")

add_subdirectory(${LLAMA_DIR} llama-build)

add_library(
    llama-android
    SHARED
    llama-android.cpp
)

target_link_libraries(
    llama-android
    llama
    android
    log
)
```

**Dependency:** Clone llama.cpp into a sibling directory of your Android project, or use a git submodule.

### 5.4 Model Loading Strategy

| Option | Pros | Cons |
|--------|------|------|
| **Bundle in APK** (`res/raw/`) | Single install, offline | APK size +100MB, slow install |
| **Bundle in assets, copy on first run** | Same as above | First launch takes time to copy |
| **Download on first launch** | Small APK | Needs network, server hosting |
| **External storage** | User manages | No app control, manual step |

**Hackathon recommendation:** Bundle the GGUF in `src/main/assets/` and copy to app-private storage on first run. The JNI bridge reads from filesystem path.

---

## 6. Workflow Model

```kotlin
// Data classes
@Entity(tableName = "workflows")
data class Workflow(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val triggerJson: String,      // serialized TriggerConfig
    val actionsJson: String,       // serialized List<WorkflowStep>
    val createdAt: Long = System.currentTimeMillis()
)

sealed class TriggerConfig {
    data class Nfc(val tagId: String?) : TriggerConfig()
    data class Time(val hour: Int, val minute: Int, val repeatDays: List<Int>) : TriggerConfig()
    data class Location(val lat: Double, val lng: Double, val radiusMeters: Float) : TriggerConfig()
    data class AppOpen(val packageName: String) : TriggerConfig()
    object Manual : TriggerConfig()
}

sealed class WorkflowStep {
    data class IntentStep(
        val packageName: String,       // e.g. "com.spotify.music"
        val action: String,            // e.g. "android.media.action.MEDIA_PLAY_FROM_SEARCH"
        val extras: Map<String, String>,
        val category: String? = null
    ) : WorkflowStep()

    data class UrlStep(
        val url: String                // e.g. "obsidian://new?name=..."
    ) : WorkflowStep()

    data class ScriptStep(
        val language: String,          // "python", "node", "bash"
        val code: String
    ) : WorkflowStep()

    data class AccessibilityStep(
        val targetPackage: String,
        val actionDescription: String  // e.g. "tap button with text 'Play'"
    ) : WorkflowStep()

    data class AppStep(
        val app: String,               // alias from catalog
        val intent: String,
        val params: Map<String, String>
    ) : WorkflowStep()
}
```

---

## 7. System Prompt (What the SLM Sees)

The `PromptBuilder` constructs a prompt that includes:

1. **Action allowlist** with parameter schemas
2. **URL scheme catalog** for apps with URL schemes
3. **Intent catalog** for apps with exported Intents
4. **JSON schema** the SLM must follow
5. **Trigger types** the app supports

```kotlin
fun buildPrompt(userRequest: String): String = """
    You are an on-device Android workflow planner. Return valid JSON only.

    Supported apps and actions:
    - spotify: play(query), play_playlist(id), pause
    - obsidian: create_note(name, content, vault), append_note(file, content)
    - things: add_todo(title, notes, when, tags)
    - bear: create_note(title, text, tags)
    - drafts: create_draft(text, tag)
    - scriptable: run_javascript(code)   -- for custom logic
    - url: open(url)                     -- fallback for any app

    Supported triggers:
    - nfc(tag_id)
    - time(hour, minute, repeat_days)
    - location(lat, lng, radius_meters)
    - app_open(package_name)
    - manual

    Output schema:
    {
      "name": "short human-readable name",
      "trigger": { "type": "nfc", "tag_id": "..." },
      "actions": [
        { "app": "spotify", "action": "play", "params": { "query": "focus playlist" } },
        { "app": "obsidian", "action": "create_note", "params": { "name": "Focus", "content": "...", "vault": "Personal" } }
      ]
    }

    User request: $userRequest
    Return JSON only.
""".trimIndent()
```

---

## 8. Execution Layer — WorkflowRunner

```kotlin
class WorkflowRunner(
    private val intentDispatcher: IntentDispatcher,
    private val urlDispatcher: UrlDispatcher,
    private val termuxDispatcher: TermuxDispatcher,
    private val accessibilityDispatcher: AccessibilityDispatcher
) {
    suspend fun run(workflow: Workflow): List<ExecutionResult> {
        val steps = Json.decodeFromString<List<WorkflowStep>>(workflow.actionsJson)
        return steps.map { step ->
            when (step) {
                is WorkflowStep.IntentStep -> intentDispatcher.dispatch(step)
                is WorkflowStep.UrlStep -> urlDispatcher.dispatch(step)
                is WorkflowStep.ScriptStep -> termuxDispatcher.dispatch(step)
                is WorkflowStep.AppStep -> dispatchAppStep(step)
                is WorkflowStep.AccessibilityStep -> accessibilityDispatcher.dispatch(step)
            }
        }
    }

    private fun dispatchAppStep(step: WorkflowStep.AppStep): ExecutionResult {
        return when (step.app) {
            "spotify" -> intentDispatcher.dispatchSpotify(step.params)
            "obsidian" -> urlDispatcher.dispatchObsidian(step.params)
            "things" -> urlDispatcher.dispatchThings(step.params)
            else -> ExecutionResult.Failure("Unknown app: ${step.app}")
        }
    }
}
```

---

## 9. Trigger Creation — Two Paths

### 9.1 Primary: Tasker Plugin API

Tasker exposes a plugin API where your app can send an Intent to create a profile + task.

```kotlin
object TaskerPluginHelper {
    private const val TASKER_PACKAGE = "net.dinglisch.android.taskerm"
    private const val ACTION_EDIT_TASK = "net.dinglisch.android.taskerm.ACTION_EDIT_TASK"
    private const val ACTION_EDIT_PROFILE = "net.dinglisch.android.taskerm.ACTION_EDIT_PROFILE"

    fun createNfcAutomation(context: Context, workflow: Workflow, tagId: String?) {
        val intent = Intent(ACTION_EDIT_PROFILE).apply {
            setPackage(TASKER_PACKAGE)
            putExtra("com.twofortyfouram.locale.intent.extra.BLURB", "NFC trigger for ${workflow.name}")
            // Bundle with trigger type + task JSON referencing the workflow ID
        }
        context.startActivity(intent)
    }
}
```

**User flow:**
1. App generates workflow
2. User taps "Create Trigger"
3. App opens Tasker with pre-filled profile
4. User taps "Save" in Tasker (one confirmation)
5. Trigger is live

### 9.2 Fallback: Native Android Triggers (No Tasker)

If Tasker is not installed, use native APIs:

| Trigger | Android API | Limitation |
|---------|------------|------------|
| **NFC** | `NfcAdapter.enableForegroundDispatch()` + `PendingIntent` | Only works when app is in foreground. For background: write NDEF with app-specific MIME type, app declares intent filter |
| **Time** | `AlarmManager.setExactAndAllowWhileIdle()` | Requires `SCHEDULE_EXACT_ALARM` permission. App must handle the broadcast receiver |
| **Location** | `GeofencingClient.addGeofences()` | Requires background location permission, battery optimization whitelist |
| **App open** | `UsageStatsManager` or `AccessibilityService` | Expensive to monitor, AccessibilityService permission |
| **Boot** | `BOOT_COMPLETED` receiver | Reliable but coarse |

**Hackathon recommendation**: Build for Tasker first (rich, reliable, demo-worthy). Add native NFC as fallback.

---

## 10. AccessibilityService Fallback

For apps with no Intent and no URL scheme, use `AccessibilityService` to read and interact with UI.

```kotlin
class WorkflowAccessibilityService : AccessibilityService() {
    fun performAction(packageName: String, description: String) {
        val rootNode = rootInActiveWindow ?: return
        // Find node by text/content-desc/view-id, then perform ACTION_CLICK or ACTION_SET_TEXT
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() {}
}
```

**Usage in demo:** Only as fallback. Keep it clean — "For apps that don't expose APIs, we can automate their UI."

---

## 11. Termux Script Execution

```kotlin
class TermuxDispatcher(private val context: Context) {
    fun dispatch(step: WorkflowStep.ScriptStep): ExecutionResult {
        val intent = Intent().apply {
            component = ComponentName("com.termux", "com.termux.app.RunCommandService")
            action = "com.termux.RUN_COMMAND"
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/${step.language}")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", step.code))
        }
        context.startService(intent)
        return ExecutionResult.Success("Script dispatched to Termux")
    }
}
```

**Requires:** Termux app installed, script file written to Termux accessible storage.

---

## 12. UI Screens

### 12.1 Home Screen
- **Prompt input** (large text field)
- **Generate button**
- **Backend selector** (mock vs llama.cpp)
- **JSON preview** (monospace, collapsible)
- **Workflow preview card** (name + action count)
- **Save button** → saves to Room DB, clears input

### 12.2 Workflow Library Screen
- Grid of saved workflows
- Each card: name, trigger icon, action count
- Tap to view detail
- Long-press to delete

### 12.3 Workflow Detail Screen
- Name (editable)
- Trigger config display
- List of actions (icon + summary)
- **"Run Now"** button → runs WorkflowRunner
- **"Set Up Trigger"** button → opens Tasker or native trigger setup
- Execution history for this workflow

### 12.4 Trigger Setup Screen
- Radio group: NFC / Time / Location / App Open / Manual
- NFC: "Write to Tag" button (uses `NfcAdapter`)
- Time: TimePicker + repeat day toggles
- Location: Map picker + radius slider
- App Open: App picker (list of installed apps)

---

## 13. Task Breakdown (Hackathon Timeline)

Assume 3-4 day hackathon. Ordered by dependency.

### Day 1 — Foundation + Inference
| Task | Owner | Hours | Blocked By |
|------|-------|-------|-----------|
| Install Android Studio, NDK, create emulator | Any | 2 | — |
| Clone llama.cpp, build Android example, verify inference | Any | 3 | Studio + NDK |
| Create Android project (Compose, Room, Gradle) | Any | 2 | — |
| Integrate llama.cpp via JNI (CMake, loadLibrary, basic generate) | Native dev | 4 | Working example |
| Add GGUF + grammar to assets, copy on first run | Any | 2 | JNI works |
| **Day 1 deliverable:** App loads model, responds to hardcoded prompt | | | |

### Day 2 — Generation + Parsing
| Task | Owner | Hours | Blocked By |
|------|-------|-------|-----------|
| Build `PromptBuilder` with app catalog + JSON schema | Any | 2 | — |
| Wire prompt → JNI generate → raw output | Any | 2 | JNI works |
| Build `WorkflowJsonParser` (extract JSON, decode to data classes) | Any | 2 | — |
| Build `SafeActionRouter` (validate actions against allowlist) | Any | 2 | Parser works |
| Build `Workflow` model + Room entities | Any | 2 | — |
| Build `WorkflowStore` (save/load/delete workflows) | Any | 2 | Room entities |
| **Day 2 deliverable:** Type prompt → SLM generates → parse → save to library | | | |

### Day 3 — Execution + Triggers
| Task | Owner | Hours | Blocked By |
|------|-------|-------|-----------|
| Build `IntentDispatcher` (explicit intents for Spotify, Obsidian, etc.) | Any | 3 | — |
| Build `UrlDispatcher` (URL schemes for Obsidian, Things, Bear, etc.) | Any | 2 | — |
| Build `WorkflowRunner` (sequentially dispatch steps) | Any | 2 | Dispatchers |
| Build `TaskerPluginHelper` (create Tasker profiles programmatically) | Any | 3 | Workflow model |
| Build native NFC helper (write tags, foreground dispatch) | Any | 2 | — |
| Build `HomeScreen` + `WorkflowLibraryScreen` Compose UI | UI dev | 4 | ViewModels |
| **Day 3 deliverable:** Saved workflow → run now → Spotify opens + Obsidian opens. NFC tag writing works. | | | |

### Day 4 — Polish + Demo
| Task | Owner | Hours | Blocked By |
|------|-------|-------|-----------|
| Build `TriggerSetupScreen` (NFC / time / location UI) | UI dev | 3 | Day 3 triggers |
| Build `WorkflowDetailScreen` with run + history | UI dev | 3 | Day 3 |
| Test full flow: prompt → generate → save → create trigger → tap NFC → execute | All | 3 | Everything |
| Demo script + backup videos | Any | 2 | Stable flow |
| **Day 4 deliverable:** Working hackathon demo | | | |

---

## 14. Demo Script

```
[Home Screen]
"I want my phone to help me focus. When I tap my desk NFC tag,
play my focus playlist on Spotify and create an Obsidian note
for today's deep work session."

[type into prompt, tap Generate]

[JSON preview slides in — shows structured workflow]
"The Gemma 3 1B model running entirely on the phone converts
my sentence into a structured plan. No server, no cloud."

[tap Save, navigate to Workflow Library]
"Here's my saved workflow. I can run it manually anytime."

[tap Workflow, tap "Set Up Trigger"]
"Or I can set up a trigger. Let's use NFC."

[Trigger Setup Screen — tap "Write to Tag", bring NFC tag to phone]
"I write the trigger to this NFC tag."

[tap "Create Tasker Profile" — Tasker opens with pre-filled profile]
"Tasker receives the profile automatically. I just tap save."

[Exit to home screen, tap NFC tag]
"Now when I tap the tag..."

[Phone screen: Spotify opens, starts playing. Obsidian opens,
new note created with title and content.]
"It just works. Across apps. Zero programming. Zero block editors."
```

---

## 15. Critical Dependencies

| Dependency | How to Get | Risk |
|-----------|-----------|------|
| **Android phone** | Borrow/buy used Pixel 6a/7/8 or Samsung A54 | HIGH if unavailable. Emulator too slow for SLM demo |
| **Tasker app** | $3.49 on Google Play. Or use 7-day free trial | LOW. Easy install |
| **Termux** | Free on F-Droid or GitHub releases (Google Play version outdated) | LOW |
| **llama.cpp Android example** | Clone `github.com/ggerganov/llama.cpp` | LOW |
| **Gemma 3 1B GGUF** | Download from HuggingFace (e.g. `bartowski/gemma-3-1b-it-GGUF`) | LOW |
| **Target apps installed** | Spotify, Obsidian, Things, Bear on demo phone | LOW |

---

## 16. Risks & Mitigations

| Risk | Likelihood | Mitigation |
|------|-----------|-----------|
| JNI build fails / linking errors | Medium | Start with llama.cpp Android example. Don't modify native code until example runs |
| SLM generates garbage JSON | Medium | Grammar file (GBNF) forces valid JSON. Heuristic fallback parser. Mock backend for demo backup |
| Inference too slow on phone | Medium | Use Q4_K_M or Q3_K_S quantization. Reduce context to 512. Cap output to 64 tokens |
| Tasker plugin API changed | Low | Use stable intent actions. Have native AlarmManager/Geofence fallback ready |
| Target app Intent/URL scheme changed | Low | Test all target apps the morning of demo. Have backup apps |
| AccessibilityService rejected by Play Store | Medium | Don't submit to Play Store during hackathon. Sideload APK. Mention it as research direction |

---

## 17. Setup Checklist (Day Zero)

```
□ Install Android Studio ( Hedgehog or newer )
□ Install SDK Platform API 34/35
□ Install Android Emulator
□ Install NDK 25.2+ via SDK Manager
□ Create Pixel 7 emulator, arm64, 6GB RAM
□ Clone llama.cpp repo
□ Build llama.cpp/examples/android in Android Studio
□ Verify example runs on emulator (UI loads, even if no model)
□ Obtain physical Android phone (borrow/buy)
□ Install Tasker on phone (free trial or purchase)
□ Install Termux on phone (F-Droid version)
□ Install Spotify, Obsidian on phone
□ Download gemma-3-1b-it.Q4_K_M.gguf (~700MB)
□ Test llama.cpp example on physical phone with GGUF
□ Measure inference speed (tokens/sec)
□ Tune quantization or context size if too slow
```

---

*Android-only implementation path. Assumes 3-4 person hackathon team with one person comfortable with Android/Kotlin, one with C++/JNI, and one with UI/Compose.*

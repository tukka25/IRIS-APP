# GemmaWorkflow — Android Implementation Path

> **Platform:** Android (Kotlin + Jetpack Compose + LiteRT-LM)
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
│          │ (LiteRT-LM)  │  │  (Room DB)   │  │  (Tasker API) │      │
│          └──────┬────────┘  └──────────────┘  └──────┬───────┘      │
│                 │                                      │              │
│                 ▼                                      ▼              │
│          ┌──────────────┐                    ┌──────────────┐        │
│          │ LitertLmEngine│                   │   Tasker     │        │
│          │ (Kotlin API) │                    │   Plugin     │        │
│          └──────┬───────┘                    │   Broadcast  │        │
│                 │                            └──────────────┘        │
│                 ▼                                                     │
│          ┌──────────────┐                                             │
│          │  LiteRT-LM   │                                             │
│          │  + .litertlm │                                             │
│          │  (GPU backend)│                                            │
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
| Inference | LiteRT-LM Kotlin API | No JNI/NDK needed — pure Kotlin AAR |
| Build | Gradle Kotlin DSL | No CMake, no native build config |
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
│   │   │   │   ├── LitertLmEngine.kt        # LiteRT-LM Kotlin API wrapper
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
│   └── res/
│       └── raw/
│       └── assets/
│           └── planner-json.gbnf            # Grammar file for constrained JSON
├── build.gradle.kts                         # App-level Gradle config
```

---

## 5. LLM Inference Layer (Critical Path)

LiteRT-LM provides a first-class Kotlin API — no JNI bridge, no CMake, no NDK needed.

### 5.1 Dependencies

```gradle
// app/build.gradle.kts
dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.10.0")
}
```

### 5.2 AndroidManifest (GPU support)

```xml
<application>
    <uses-native-library android:name="libOpenCL.so" android:required="false" />
    <uses-native-library android:name="libVulkan.so" android:required="false" />
</application>
```

### 5.3 Kotlin Engine Wrapper

```kotlin
class LitertLmEngine {

    private var engine: Engine? = null

    suspend fun initialize(
        modelPath: String,
        cacheDir: String? = null,
        backend: Backend = Backend.GPU()
    ) = withContext(Dispatchers.Default) {
        close()
        val config = EngineConfig(
            modelPath = modelPath,
            backend = backend,
            cacheDir = cacheDir
        )
        engine = Engine(config).also { it.initialize() }
    }

    suspend fun generate(prompt: String): String = withContext(Dispatchers.Default) {
        val currentEngine = engine
            ?: throw IllegalStateException("Engine not initialized.")

        currentEngine.createConversation(
            ConversationConfig(
                samplerConfig = SamplerConfig(
                    topK = 40,
                    topP = 0.95,
                    temperature = 0.7
                )
            )
        ).use { conv ->
            conv.sendMessage(prompt).toString()
        }
    }

    fun close() {
        engine?.close()
        engine = null
    }
}
```

### 5.4 Model Format

LiteRT-LM uses `.litertlm` files. Pre-converted Gemma models are on HuggingFace:

  https://huggingface.co/litert-community

Push to device:

```bash
adb push gemma-4-E2B-it.litertlm \
  /sdcard/Android/data/com.gemmaworkflow/files/models/
```

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
        val packageName: String,
        val action: String,
        val extras: Map<String, String>,
        val category: String? = null
    ) : WorkflowStep()

    data class UrlStep(
        val url: String
    ) : WorkflowStep()

    data class ScriptStep(
        val language: String,          // "python", "node", "bash"
        val code: String
    ) : WorkflowStep()

    data class AccessibilityStep(
        val targetPackage: String,
        val actionDescription: String
    ) : WorkflowStep()

    data class AppStep(
        val app: String,
        val intent: String,
        val params: Map<String, String>
    ) : WorkflowStep()
}
```

---

## 7. Planner, Runner, Triggers & UI

See `ARCHITECTURE.md` for the full design doc covering:
- System prompt construction and JSON schema
- WorkflowRunner and Intent/URL dispatchers
- NFC, Tasker, and native trigger paths
- Compose screen layouts and navigation

## 8. Build & Run (Quickstart)

```bash
# 1. Clone LiteRT-LM for GPU native libs and tools (optional)
scripts/setup_litert_lm.sh

# 2. Build the Android app
./gradlew installDebug

# 3. Push the model (Gemma 4 E2B IT, 2.4 GB)
adb push local_models/gemma-4-E2B-it.litertlm \
  /sdcard/Android/data/com.gemmaworkflow/files/models/

# 4. Open the app, tap "Load model", enter a prompt, tap "Generate"
```

### Required installs

| Tool | Purpose |
|------|---------|
| Android Studio (Hedgehog+) | IDE + SDK manager |
| Android SDK 35 | Compile SDK |
| Kotlin 2.3.20 | Language, Compose compiler plugin |
| LiteRT-LM (clone) | GPU native libs (optional: scripts/setup_litert_lm.sh) |
| Gemma .litertlm model | Download from HuggingFace litert-community |

---

## 9. Key Differences from llama.cpp

| Concern | llama.cpp (old) | LiteRT-LM (new) |
|---------|----------------|-----------------|
| Integration | JNI bridge + CMake + NDK | Pure Kotlin AAR from Google Maven |
| Model format | `.gguf` | `.litertlm` |
| GPU support | Vulkan (manual config) | OpenCL/Vulkan (auto-selected) |
| Build complexity | NDK, CMake, cross-compilation | None — just a Gradle dependency |
| API surface | C++ JNI functions | Kotlin `Engine` / `Conversation` |
| Tokenization | Manual `llama_tokenize()` | Built into `Conversation` |
| Sampling | Manual chain setup | `SamplerConfig` (topK, topP, temp) |

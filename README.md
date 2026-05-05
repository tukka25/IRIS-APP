# GemmaWorkflow — Android On-Device AI

> **Platform:** Android (Kotlin + Jetpack Compose + LiteRT-LM)
> **Goal:** On-device SLM that turns natural language into executable cross-app workflows.

---

## Current State (Built & Working)

This is a **LiteRT-LM GPU smoke-test app**. It loads a Gemma 4 model on the phone GPU and runs local inference — no cloud, no JNI, no C++.

```
User prompt  →  LiteRT-LM GPU inference  →  Generated text in UI
```

### What's Built

| Layer | File | Purpose |
|-------|------|---------|
| Inference | `LitertLmEngine.kt` | Wraps LiteRT-LM `Engine` + `Conversation` API. `Backend.GPU()` by default. |
| Model locator | `ModelFileLocator.kt` | Resolves `.litertlm` path on device (`gemma-4-E2B-it.litertlm`) |
| ViewModel | `LlamaSmokeViewModel.kt` | `loadModel()` → `engine.initialize()` → `engine.generate()` |
| UI state | `LlamaSmokeUiState.kt` | Holds model path, prompt, response, load/error state |
| Screen | `MainActivity.kt` | Compose: model path display, load button, prompt input, generate button, response output |
| Theme | `GemmaWorkflowTheme.kt` | Material 3 dark theme |
| Manifest | `AndroidManifest.xml` | GPU lib declarations (`libOpenCL.so`, `libVulkan.so`) |
| App class | `GemmaWorkflowApp.kt` | Empty Application class |

### What's Stubbed (`.gitkeep` placeholders, not built)

```
domain/planner/     domain/parser/    domain/runner/
domain/safety/      domain/triggers/
data/local/dao/     data/local/database/   data/local/entity/
data/repository/    data/settings/
platform/dispatch/  platform/nfc/    platform/tasker/
ui/workflows/       ui/triggers/     ui/components/
```

---

## Why LiteRT-LM Instead of llama.cpp

| | llama.cpp (DELETED) | LiteRT-LM (ACTIVE) |
|---|---|---|
| Integration | C++ JNI + CMake + NDK | Pure Kotlin AAR (`com.google.ai.edge.litertlm:litertlm-android:0.10.0`) |
| GPU | Vulkan only, manual | OpenCL + Vulkan, auto-selected |
| Model | `.gguf` (community format) | `.litertlm` (Google's optimized format) |
| Build | Cross-compile C++, link native libs | One Gradle dependency, zero native code |
| API | Manual tokenize/sample/decode | `Engine.initialize()` → `Conversation.sendMessage()` |

---

## Technology Stack

| Layer | Choice | Why |
|-------|--------|-----|
| Language | Kotlin 2.3.20 | Coroutines, sealed classes, null safety |
| UI | Jetpack Compose + Material 3 | Declarative, fast iteration |
| State | ViewModel + StateFlow | Survives rotation, observable |
| Inference | LiteRT-LM 0.10.0 | Google's on-device LLM framework, GPU-accelerated |
| Model | Gemma 4 E2B IT `.litertlm` | Google's edge-optimized SLM, 2.4 GB |
| GPU backend | OpenCL / Vulkan | Auto-selected by LiteRT-LM on device |
| Build | Gradle Kotlin DSL | Standard Android, no CMake/NDK |
| Min SDK | 26 (Android 8) | Covers 95%+ of active devices |

---

## Source Tree (What Actually Exists)

```
app/src/main/java/com/gemmaworkflow/
├── app/
│   └── GemmaWorkflowApp.kt                  # Application class
├── platform/inference/litert/
│   ├── LitertLmEngine.kt                    # LiteRT-LM wrapper (GPU)
│   └── ModelFileLocator.kt                  # Locates .litertlm on device
└── ui/
    ├── MainActivity.kt                      # Compose smoke-test screen
    ├── home/
    │   ├── LlamaSmokeViewModel.kt           # Load + generate
    │   └── LlamaSmokeUiState.kt             # UI state
    └── theme/
        └── GemmaWorkflowTheme.kt            # Material 3 theme

LiteRT-LM/                                    # Cloned sibling (GPU libs + CLI)
└── prebuilt/android_arm64/

local_models/
└── gemma-4-E2B-it.litertlm                   # 2.4 GB model (gitignored)
```

---

## Build & Run

```bash
# 1. Push the model to your phone (2.4 GB)
adb push local_models/gemma-4-E2B-it.litertlm \
  /sdcard/Android/data/com.gemmaworkflow/files/models/

# 2. Build and install
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew installDebug

# 3. Open app → tap "Load model" → status shows "Loaded — GPU (LiteRT-LM)"
# 4. Enter prompt → tap "Generate"
```

---

## Planned (Next Milestones)

The full architecture is designed in `ARCHITECTURE.md`. The smoke test is step one. After GPU inference is stable:

1. **Mock planner** — hardcoded JSON workflows for UI/runner testing
2. **Workflow parser + validator** — extract JSON from model output, validate against action allowlist
3. **Workflow runner** — dispatch Android intents and URL schemes
4. **Persistence** — Room DB for saved workflows and execution history
5. **Triggers** — NFC tag, manual run, optional Tasker plugin
6. **Full LiteRT-LM planner** — replace mock with real SLM-generated workflows

---

## Docs

| File | What |
|------|------|
| `ARCHITECTURE.md` | Full design: packages, contracts, milestones, risk table |
| `TASKS.md` | Day-by-day task breakdown for hackathon build |
| `docs/demo/litert_lm_android_gpu_spike.md` | This branch's spike notes |
| `docs/demo/emulator_smoke_test.md` | Smoke test checklist |

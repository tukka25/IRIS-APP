# LiteRT-LM Android GPU Spike

This branch replaces llama.cpp with the official LiteRT-LM Kotlin API for
running language models on a physical Android phone with GPU acceleration.

## What Changed

- **Removed** all llama.cpp native code (C++ JNI bridge, CMake, NDK config).
- **Added** LiteRT-LM Kotlin AAR dependency from Google Maven.
- **Created** `LitertLmEngine` — pure Kotlin wrapper around LiteRT-LM's
  `Engine` / `Conversation` API. No JNI bridge needed.
- **Updated** `ModelFileLocator` to expect `.litertlm` model files.
- **Added** GPU native library declarations in `AndroidManifest.xml`
  (OpenCL + Vulkan) for GPU acceleration.
- **Updated** build config: removed `externalNativeBuild` and `ndk` blocks.

## Model Format

LiteRT-LM uses `.litertlm` files, not `.gguf`.

Pre-converted Gemma models are available on HuggingFace:

  https://huggingface.co/litert-community

Push a model to the device:

```bash
adb push gemma3-1b-it.litertlm \
  /sdcard/Android/data/com.gemmaworkflow/files/models/
```

## GPU Backend

The app uses `Backend.GPU()` which leverages OpenCL or Vulkan on the device.
The `AndroidManifest.xml` declares both as optional native libraries:

```xml
<uses-native-library android:name="libOpenCL.so" android:required="false" />
<uses-native-library android:name="libVulkan.so" android:required="false" />
```

## Prerequisites

1. Physical Android phone connected with `adb`.
2. LiteRT-LM cloned as sibling repo (for GPU native libs if needed):
   ```bash
   scripts/setup_litert_lm.sh
   ```
3. A `.litertlm` model file pushed to the device.

## Running

1. Build and install the app:
   ```bash
   ./gradlew installDebug
   ```
2. Push a model:
   ```bash
   adb push gemma3-1b-it.litertlm \
     /sdcard/Android/data/com.gemmaworkflow/files/models/
   ```
3. Open the app, tap "Load model", then enter a prompt and tap "Generate".

## What Success Looks Like

- The LiteRT-LM engine initializes on the phone with GPU backend.
- GPU backend starts without falling back to CPU.
- Prompt generation completes successfully.
- The load status shows "Loaded — GPU (LiteRT-LM)".

## Architecture

```
Kotlin UI (Compose)
    └── LlamaSmokeViewModel
        └── LitertLmEngine (Kotlin)
            └── com.google.ai.edge.litertlm.Engine (AAR)
                └── OpenCL / Vulkan GPU backend (native)
```

No JNI bridge needed — LiteRT-LM's Kotlin API handles everything.

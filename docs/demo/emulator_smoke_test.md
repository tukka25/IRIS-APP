# Emulator / Device Smoke Test

This doc covers the LiteRT-LM GPU smoke-test flow on Android emulator or physical device.

## Model

LiteRT-LM uses `.litertlm` files. Pre-converted Gemma models are on HuggingFace:

  https://huggingface.co/litert-community

The model lives outside Git:

```text
local-models/gemma3-1b-it.litertlm
```

Push to device:

```bash
adb push local-models/gemma3-1b-it.litertlm \
  /sdcard/Android/data/com.gemmaworkflow/files/models/
```

## Setup

Clone LiteRT-LM next to this repo for GPU native libs and CLI tools:

```bash
scripts/setup_litert_lm.sh
```

This clones `https://github.com/google-ai-edge/LiteRT-LM` into:

```text
../LiteRT-LM
```

## Build

The app uses LiteRT-LM's Kotlin AAR from Google Maven — no CMake, no NDK, no native build.

```bash
./gradlew installDebug
```

## Smoke Test

1. Open the app on device
2. Verify the model path shows correctly
3. Tap "Load model" — status should show "Loaded — GPU (LiteRT-LM)"
4. Enter a prompt and tap "Generate"
5. Verify response is non-empty and coherent

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| Model not found | `.litertlm` not pushed | Check path, re-push with adb |
| GPU fallback to CPU | OpenCL/Vulkan missing | Verify device supports GPU; check AndroidManifest lib declarations |
| Engine init timeout | Model too large | Use smaller model (1B param) |
| Gradle sync fails | LiteRT-LM AAR not found | Check `google()` in repositories; sync again |

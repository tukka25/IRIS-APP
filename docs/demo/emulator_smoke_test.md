# Emulator Smoke Test

This branch is for the first Gemma GGUF load/query test on Android.

## Local Files

The GGUF model lives outside Git:

```text
local-models/gemma-planner-dev.Q4_K_M.gguf
```

The app expects the model on the emulator/device at:

```text
/sdcard/Android/data/com.gemmaworkflow/files/models/gemma-planner-dev.Q4_K_M.gguf
```

## Setup

Clone llama.cpp next to this repo:

```bash
scripts/setup_llama_cpp.sh
```

Open this repo in Android Studio and sync the Gradle project. The native build expects:

```text
../llama.cpp
```

## Push The Model

After an emulator or phone is attached:

```bash
scripts/push_model_to_emulator.sh
```

If the package directory does not exist yet, install or run the app once, then run the script again.

## App Flow

1. Launch GemmaWorkflow.
2. Tap `Load model`.
3. Enter a short prompt.
4. Tap `Generate`.
5. Check the response text and Logcat tag `GemmaLlama`.

## Notes

- The model is not packaged into the APK because it is too large for fast hackathon iteration.
- This branch intentionally runs the model through the CPU backend for predictable emulator testing.
- The first goal is a successful load and one short response, not workflow planning quality.

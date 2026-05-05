#!/usr/bin/env bash
set -euo pipefail

# Push a .litertlm model and LiteRT-LM CLI binary to a connected Android device
# and run the model with GPU or CPU backend.
#
# Usage:
#   scripts/run_litert_lm_android.sh <model.litertlm> [gpu|cpu]
#
# Defaults:
#   backend = gpu

MODEL_PATH="${1:-local-models/model.litertlm}"
BACKEND="${2:-gpu}"
LITERT_LM_DIR="${3:-../LiteRT-LM}"
DEVICE_DIR="${DEVICE_DIR:-/data/local/tmp/litertlm}"

if [ ! -f "$MODEL_PATH" ]; then
    echo "Model not found at ${MODEL_PATH}" >&2
    echo "Download a .litertlm model from: https://huggingface.co/litert-community" >&2
    exit 1
fi

# Find adb
if ! command -v adb >/dev/null 2>&1; then
    if [ -x "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
        ADB="$HOME/Library/Android/sdk/platform-tools/adb"
    elif [ -x "$ANDROID_HOME/platform-tools/adb" ]; then
        ADB="$ANDROID_HOME/platform-tools/adb"
    else
        echo "adb not found. Add Android SDK platform-tools to PATH." >&2
        exit 1
    fi
else
    ADB="adb"
fi

echo "Pushing model to device..."
"$ADB" shell "mkdir -p '$DEVICE_DIR'"
"$ADB" push "$MODEL_PATH" "$DEVICE_DIR/model.litertlm"

# If we have the CLI binary from a LiteRT-LM build, push it too
if [ -x "$LITERT_LM_DIR/bazel-bin/runtime/engine/litert_lm_main" ]; then
    echo "Pushing LiteRT-LM CLI binary..."
    "$ADB" push "$LITERT_LM_DIR/bazel-bin/runtime/engine/litert_lm_main" "$DEVICE_DIR/litert_lm_main"

    if [ "$BACKEND" = "gpu" ]; then
        echo "Pushing GPU native libraries..."
        "$ADB" push "$LITERT_LM_DIR/prebuilt/android_arm64/." "$DEVICE_DIR/" 2>/dev/null || true
        "$ADB" shell "chmod +x '$DEVICE_DIR/litert_lm_main' && LD_LIBRARY_PATH='$DEVICE_DIR' '$DEVICE_DIR/litert_lm_main' --backend=gpu --model_path='$DEVICE_DIR/model.litertlm'"
    else
        "$ADB" shell "chmod +x '$DEVICE_DIR/litert_lm_main' && '$DEVICE_DIR/litert_lm_main' --backend=cpu --model_path='$DEVICE_DIR/model.litertlm'"
    fi
else
    echo ""
    echo "Model pushed to device. LiteRT-LM CLI binary not found (Bazel build skipped)."
    echo ""
    echo "To run via the Android app instead:"
    echo "  1. Build and install: ./gradlew installDebug"
    echo "  2. Push model to app dir:"
    echo "     $ADB push $MODEL_PATH /sdcard/Android/data/com.gemmaworkflow/files/models/"
    echo "  3. Open app and tap 'Load model'"
fi

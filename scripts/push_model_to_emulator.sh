#!/usr/bin/env bash
set -euo pipefail

PACKAGE_NAME="com.gemmaworkflow"
MODEL_NAME="gemma-planner-dev.Q4_K_M.gguf"
LOCAL_MODEL="${1:-local-models/${MODEL_NAME}}"
REMOTE_DIR="/sdcard/Android/data/${PACKAGE_NAME}/files/models"
REMOTE_MODEL="${REMOTE_DIR}/${MODEL_NAME}"

if ! command -v adb >/dev/null 2>&1; then
    if [ -x "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
        ADB="$HOME/Library/Android/sdk/platform-tools/adb"
    else
        echo "adb not found. Add Android SDK platform-tools to PATH." >&2
        exit 1
    fi
else
    ADB="adb"
fi

if [ ! -f "$LOCAL_MODEL" ]; then
    echo "Model not found: ${LOCAL_MODEL}" >&2
    exit 1
fi

"$ADB" shell "mkdir -p '${REMOTE_DIR}'"
"$ADB" push "$LOCAL_MODEL" "$REMOTE_MODEL"
echo "Pushed model to ${REMOTE_MODEL}"


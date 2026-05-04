#!/usr/bin/env bash
set -euo pipefail

# Clone LiteRT-LM as a sibling repo for GPU native libraries and CLI tools.
#
# Usage:
#   scripts/setup_litert_lm.sh [target_dir] [ref]
#
# Defaults:
#   target_dir = ../LiteRT-LM
#   ref        = v0.10.2

TARGET_DIR="${1:-../LiteRT-LM}"
TARGET_REF="${2:-v0.10.2}"

if [ -f "$TARGET_DIR/README.md" ]; then
    echo "LiteRT-LM already exists at ${TARGET_DIR}"
else
    echo "Cloning LiteRT-LM into ${TARGET_DIR}..."
    git clone https://github.com/google-ai-edge/LiteRT-LM.git "$TARGET_DIR"
fi

git -C "$TARGET_DIR" fetch --tags
git -C "$TARGET_DIR" checkout "$TARGET_REF"

echo ""
echo "LiteRT-LM ready at ${TARGET_DIR} on ${TARGET_REF}"
echo ""
echo "Next steps:"
echo "  1. Add the Gradle dependency in app/build.gradle.kts (already done):"
echo "     implementation(\"com.google.ai.edge.litertlm:litertlm-android:latest.release\")"
echo ""
echo "  2. Get a .litertlm model from HuggingFace:"
echo "     https://huggingface.co/litert-community"
echo ""
echo "  3. Push the model to your device:"
echo "     adb push <model>.litertlm /sdcard/Android/data/com.gemmaworkflow/files/models/"

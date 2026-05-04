#!/usr/bin/env bash
set -euo pipefail

LITERT_LM_DIR="${1:-../LiteRT-LM}"

if [ ! -f "$LITERT_LM_DIR/README.md" ]; then
    echo "LiteRT-LM checkout not found at ${LITERT_LM_DIR}. Run scripts/setup_litert_lm.sh first." >&2
    exit 1
fi

if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    echo "ANDROID_NDK_HOME is not set. LiteRT-LM Android builds require NDK r28b or newer." >&2
    exit 1
fi

if command -v bazelisk >/dev/null 2>&1; then
    BAZEL="bazelisk"
elif command -v bazel >/dev/null 2>&1; then
    BAZEL="bazel"
else
    echo "Neither bazelisk nor bazel is installed." >&2
    exit 1
fi

(
    cd "$LITERT_LM_DIR"
    "$BAZEL" build --config=android_arm64 //runtime/engine:litert_lm_main
)

echo "Built LiteRT-LM Android demo binary."

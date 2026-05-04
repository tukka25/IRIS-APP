#!/usr/bin/env bash
set -euo pipefail

TARGET_DIR="${1:-../llama.cpp}"

if [ -f "$TARGET_DIR/CMakeLists.txt" ]; then
    echo "llama.cpp already exists at ${TARGET_DIR}"
    exit 0
fi

if [ -d "$TARGET_DIR/.git" ]; then
    echo "Found incomplete llama.cpp checkout at ${TARGET_DIR}; fetching source files."
    git -C "$TARGET_DIR" fetch origin master
    git -C "$TARGET_DIR" checkout -f FETCH_HEAD
    echo "llama.cpp repaired at ${TARGET_DIR}"
    exit 0
fi

git clone https://github.com/ggml-org/llama.cpp "$TARGET_DIR"
echo "llama.cpp cloned to ${TARGET_DIR}"

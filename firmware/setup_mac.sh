#!/usr/bin/env bash
# KineticAI — one-time Mac setup for flashing M5StickC PLUS2
# Run this once after cloning/scp'ing the firmware folder.

set -euo pipefail

echo "▶ Installing arduino-cli via Homebrew..."
if ! command -v arduino-cli >/dev/null 2>&1; then
    brew install arduino-cli
else
    echo "  arduino-cli already installed ($(arduino-cli version | head -1))"
fi

echo "▶ Configuring M5Stack board manager..."
arduino-cli config init --overwrite >/dev/null 2>&1 || true
arduino-cli config add board_manager.additional_urls \
    https://static-cdn.m5stack.com/resource/arduino/package_m5stack_index.json 2>/dev/null || true

echo "▶ Updating board index..."
arduino-cli core update-index

echo "▶ Installing m5stack:esp32 core (~500 MB, one-time)..."
arduino-cli core install m5stack:esp32

echo "▶ Installing required libraries..."
# Firmware talks to external sensors via raw I2C (Wire.h) — no vendor libs needed.
# NimBLE-Arduino is used instead of the built-in Bluedroid BLE stack (smaller IRAM).
libs=(
    "M5Unified"
    "NimBLE-Arduino"
)
for lib in "${libs[@]}"; do
    echo "  • $lib"
    arduino-cli lib install "$lib"
done

echo ""
echo "✓ Setup complete."
echo ""
echo "Next steps:"
echo "  1. Plug in your M5StickC PLUS2 via USB-C"
echo "  2. chmod +x flash.sh"
echo "  3. ./flash.sh                 # flash left boot"
echo "  4. ./flash.sh --side R        # flash right boot (after swapping sticks)"

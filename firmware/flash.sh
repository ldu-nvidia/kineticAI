#!/usr/bin/env bash
# KineticAI — one-command firmware flash
#
# Usage (from your Mac, inside ~/kineticai/firmware/):
#   ./flash.sh           → sync + compile + upload
#   ./flash.sh compile   → sync + compile only (no upload)
#   ./flash.sh sync      → sync only (pull latest code from remote)
#   ./flash.sh monitor   → open serial monitor at 115200
#   ./flash.sh --side R  → flip BOOT_SIDE to 'R' before building
#
# First time only:
#   chmod +x flash.sh
#   edit REMOTE_HOST below to your SSH alias
#
# Env vars you can override:
#   REMOTE_HOST   SSH alias for the dev box (default: below)
#   REMOTE_PATH   path to firmware dir on dev box
#   PORT          serial port (default: auto-detect first /dev/cu.usbserial-*)
#   FQBN          board FQBN (default: M5StickC Plus2)

set -euo pipefail

# ── Config ─────────────────────────────────────────────────────────
REMOTE_HOST="${REMOTE_HOST:-rtx-pro-6000}"
REMOTE_PATH="${REMOTE_PATH:-/home/ldu/repos/kineticAI/firmware/KineticAI_IMU/}"
LOCAL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/KineticAI_IMU"
FQBN="${FQBN:-m5stack:esp32:m5stack_stickc_plus}"
# ───────────────────────────────────────────────────────────────────

cmd="${1:-all}"
side=""
if [[ "${1:-}" == "--side" ]]; then
    side="$2"
    cmd="all"
    shift 2
fi

autodetect_port() {
    local p
    p=$(ls /dev/cu.usbserial-* 2>/dev/null | head -n1 || true)
    if [[ -z "$p" ]]; then
        p=$(ls /dev/cu.wchusbserial* 2>/dev/null | head -n1 || true)
    fi
    echo "$p"
}

PORT="${PORT:-$(autodetect_port)}"

do_sync() {
    echo "→ Syncing from ${REMOTE_HOST}:${REMOTE_PATH}"
    mkdir -p "$LOCAL_DIR"
    # rsync is faster than scp for repeat updates (only copies changed files)
    rsync -az --delete \
        --exclude 'build/' \
        "${REMOTE_HOST}:${REMOTE_PATH}" "${LOCAL_DIR}/"
}

do_flip_side() {
    if [[ -n "$side" ]]; then
        if [[ "$side" != "L" && "$side" != "R" ]]; then
            echo "✖ --side must be L or R (got: $side)"; exit 1
        fi
        echo "→ Setting BOOT_SIDE to '$side'"
        # Portable in-place sed for macOS
        sed -i '' "s/^#define BOOT_SIDE '[LR]'/#define BOOT_SIDE '$side'/" \
            "${LOCAL_DIR}/KineticAI_IMU.ino"
    fi
}

do_compile() {
    echo "→ Compiling for ${FQBN}"
    arduino-cli compile --fqbn "$FQBN" "$LOCAL_DIR"
}

do_upload() {
    if [[ -z "$PORT" ]]; then
        echo "✖ No serial port found. Plug in the M5 via USB-C (data cable) and retry."
        echo "   Or set PORT=/dev/cu.usbserial-XXXXX ./flash.sh"
        exit 1
    fi
    echo "→ Uploading to $PORT"
    arduino-cli upload -p "$PORT" --fqbn "$FQBN" "$LOCAL_DIR"
    echo "✓ Upload complete. M5 will reboot and start advertising as KineticAI-$(grep -o "#define BOOT_SIDE '[LR]'" "${LOCAL_DIR}/KineticAI_IMU.ino" | grep -o "[LR]")"
}

do_monitor() {
    if [[ -z "$PORT" ]]; then echo "✖ No serial port found."; exit 1; fi
    echo "→ Serial monitor at $PORT (Ctrl+A then K to exit)"
    arduino-cli monitor -p "$PORT" -c baudrate=115200
}

case "$cmd" in
    sync)    do_sync ;;
    compile) do_sync; do_flip_side; do_compile ;;
    upload)  do_upload ;;
    monitor) do_monitor ;;
    all|"")  do_sync; do_flip_side; do_compile; do_upload ;;
    *) echo "Usage: $0 [sync|compile|upload|monitor|all] [--side L|R]"; exit 1 ;;
esac

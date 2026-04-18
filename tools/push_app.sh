#!/usr/bin/env bash
# KineticAI — one-command Android app build+deploy from remote to phone via ADB WiFi.
#
# Workflow:
#   1. SSH to remote compute, trigger gradle build
#   2. rsync the generated APK to this Mac
#   3. adb install -r over WiFi
#   4. Optionally launch the app
#
# Usage (from your Mac, anywhere):
#   ./push_app.sh                 → sync, build, install, launch
#   ./push_app.sh build           → just build on remote (no install)
#   ./push_app.sh install         → skip build, just install latest pulled APK
#   ./push_app.sh launch          → just launch the app on phone
#   ./push_app.sh logs            → tail logcat filtered to our app
#   ./push_app.sh --release       → build + install release APK instead of debug
#
# First time only:
#   chmod +x push_app.sh
#   ./setup_mac_adb.sh            (one-time ADB + phone pairing)
#   edit REMOTE_HOST below
#
# Env vars you can override:
#   REMOTE_HOST     SSH alias for dev box (default: rtx-pro-6000)
#   REMOTE_PATH     repo on remote (default: /home/ldu/repos/kineticAI)
#   PHONE_ADB       phone ADB address (auto-detected if unset)
#   APK_CACHE       local dir for APKs (default: ~/kineticai/apk)
#   PKG             Android package (default: com.kineticai.app)

set -euo pipefail

# ── Config ─────────────────────────────────────────────────────────
REMOTE_HOST="${REMOTE_HOST:-rtx-pro-6000}"
REMOTE_PATH="${REMOTE_PATH:-/home/ldu/repos/kineticAI}"
APK_CACHE="${APK_CACHE:-$HOME/kineticai/apk}"
PKG="${PKG:-com.kineticai.app}"
ACTIVITY="${ACTIVITY:-com.kineticai.app.MainActivity}"
# ───────────────────────────────────────────────────────────────────

BUILD_TYPE="debug"
CMD="${1:-all}"

if [[ "${1:-}" == "--release" ]]; then
    BUILD_TYPE="release"
    CMD="all"
    shift
fi

GRADLE_TASK="assembleDebug"
APK_NAME="app-debug.apk"
if [[ "$BUILD_TYPE" == "release" ]]; then
    GRADLE_TASK="assembleRelease"
    APK_NAME="app-release-unsigned.apk"
fi

REMOTE_APK="${REMOTE_PATH}/app/build/outputs/apk/${BUILD_TYPE}/${APK_NAME}"
LOCAL_APK="${APK_CACHE}/${APK_NAME}"

mkdir -p "$APK_CACHE"

autodetect_phone_adb() {
    # If an existing adb device is listed (State = device, not offline), use it.
    local line
    line=$(adb devices 2>/dev/null | awk '/[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+:[0-9]+[[:space:]]+device/ {print $1; exit}')
    if [[ -n "$line" ]]; then
        echo "$line"
        return 0
    fi
    # Or any USB device as fallback (still works, just not wireless)
    line=$(adb devices 2>/dev/null | awk 'NR>1 && $2 == "device" {print $1; exit}')
    echo "$line"
}

get_phone_adb() {
    if [[ -n "${PHONE_ADB:-}" ]]; then
        echo "$PHONE_ADB"
        return
    fi
    local p
    p=$(autodetect_phone_adb)
    if [[ -z "$p" ]]; then
        echo "✖ No ADB device found." >&2
        echo "   Run: ./setup_mac_adb.sh   (one-time pairing)" >&2
        echo "   Or verify: adb devices" >&2
        exit 1
    fi
    echo "$p"
}

do_build() {
    echo "→ Building ${BUILD_TYPE} on ${REMOTE_HOST}"
    ssh "$REMOTE_HOST" "cd ${REMOTE_PATH} && ./gradlew ${GRADLE_TASK} --console=plain" \
        || { echo "✖ Remote build failed"; exit 1; }
}

do_pull() {
    echo "→ Pulling APK from ${REMOTE_HOST}"
    rsync -az --progress "${REMOTE_HOST}:${REMOTE_APK}" "${LOCAL_APK}" \
        || { echo "✖ APK pull failed. Did the build produce ${REMOTE_APK}?"; exit 1; }
    local sz
    sz=$(du -h "$LOCAL_APK" | cut -f1)
    echo "✓ APK pulled: $LOCAL_APK ($sz)"
}

do_install() {
    local dev
    dev=$(get_phone_adb)
    echo "→ Installing on $dev"
    if [[ ! -f "$LOCAL_APK" ]]; then
        echo "✖ No APK at $LOCAL_APK — run '$0 build' first"
        exit 1
    fi
    adb -s "$dev" install -r "$LOCAL_APK"
    echo "✓ Installed: $PKG"
}

do_launch() {
    local dev
    dev=$(get_phone_adb)
    echo "→ Launching $PKG on $dev"
    adb -s "$dev" shell am start -n "${PKG}/${ACTIVITY}" >/dev/null
    echo "✓ Launched"
}

do_logs() {
    local dev
    dev=$(get_phone_adb)
    echo "→ Tailing logcat on $dev (Ctrl+C to stop). Filter: $PKG"
    adb -s "$dev" logcat --pid="$(adb -s "$dev" shell pidof "$PKG" 2>/dev/null || echo 0)" 2>/dev/null \
        || adb -s "$dev" logcat | grep -i "kineticai\|$PKG"
}

case "$CMD" in
    build)    do_build; do_pull ;;
    pull)     do_pull ;;
    install)  do_install ;;
    launch)   do_launch ;;
    logs)     do_logs ;;
    all|"")   do_build; do_pull; do_install; do_launch ;;
    *) echo "Usage: $0 [build|pull|install|launch|logs|all] [--release]"; exit 1 ;;
esac

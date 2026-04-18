#!/usr/bin/env bash
# KineticAI — one-time Mac setup for wireless ADB to your Galaxy phone.
#
# Run this once. After it succeeds, `./push_app.sh` works forever without USB.
#
# Requirements on the PHONE (Galaxy S26 Ultra):
#   1. Settings → About phone → tap "Build number" 7× to enable Developer Options
#   2. Settings → Developer Options → enable "Wireless debugging"
#   3. Keep the Wireless debugging screen open during pairing

set -euo pipefail

echo "▶ Installing ADB via Homebrew..."
if ! command -v adb >/dev/null 2>&1; then
    brew install android-platform-tools
else
    echo "  adb already installed ($(adb --version | head -1))"
fi

echo ""
echo "=============================================================="
echo "  Wireless debugging pairing — do this on your Galaxy phone:"
echo "  "
echo "  Settings → Developer options → Wireless debugging →"
echo "    tap 'Pair device with pairing code'"
echo "  "
echo "  You'll see something like:"
echo "    IP address & port: 192.168.1.42:37123"
echo "    Wi-Fi pairing code: 123456"
echo "=============================================================="
echo ""

read -r -p "Phone pairing IP:PORT (e.g. 192.168.1.42:37123): " PAIR_ADDR
read -r -p "Pairing code (6 digits): " PAIR_CODE

echo ""
echo "▶ Pairing with phone..."
adb pair "$PAIR_ADDR" "$PAIR_CODE"

echo ""
echo "=============================================================="
echo "  Now tap the main 'Wireless debugging' toggle on the phone"
echo "  (below 'Pair device...'). You'll see a DIFFERENT IP:PORT —"
echo "  that's the connect address. Shown right under 'Wireless"
echo "  debugging' at the top of the same screen."
echo "=============================================================="
echo ""

read -r -p "Phone connect IP:PORT (e.g. 192.168.1.42:5555): " CONN_ADDR

echo ""
echo "▶ Connecting..."
adb connect "$CONN_ADDR"

sleep 1

echo ""
echo "▶ Verifying..."
if adb devices | grep -q "$CONN_ADDR.*device$"; then
    echo "✓ Connected successfully."
    echo ""
    echo "Listed devices:"
    adb devices
    echo ""
    echo "Next steps:"
    echo "  • Run ./push_app.sh to build + install + launch the app"
    echo "  • Phone will auto-reconnect after sleep as long as Wireless"
    echo "    debugging stays enabled on the phone"
    echo ""
    echo "If it disconnects (after reboot, network switch, or long sleep):"
    echo "  adb connect $CONN_ADDR"
    echo "  # No re-pairing needed unless you 'Forget' it on the phone"
else
    echo "✖ Connection verification failed."
    echo "  Try: adb connect $CONN_ADDR"
    echo "  Or check 'adb devices' manually."
    exit 1
fi

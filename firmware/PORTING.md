# Porting KineticAI Firmware to New Hardware

This document is the canonical "how to port KineticAI to a new board" guide.
If it takes more than ~2 weeks to get a minimum-viable port running, something
has gone wrong with the architecture and we should revisit this doc.

## Architectural overview

The firmware is organized into three layers:

```
┌────────────────────────────────────────────────┐
│  Portable layer (core_*.h)                     │  ← never changes across boards
│   turn detection, scoring, packet packing,     │
│   state machine, protocol logic                │
├────────────────────────────────────────────────┤
│  Hardware Abstraction Layer (platform.h)       │  ← stable interface
│   ~15 functions: IMU, display, buttons,        │
│   buzzer, BLE, battery, power modes            │
├────────────────────────────────────────────────┤
│  Board driver (board_<name>.h)                 │  ← changes per board
│   implements platform.h using the board's      │
│   SDK: M5Unified / Nordic SDK / Zephyr / etc.  │
└────────────────────────────────────────────────┘
```

**Only the board driver changes when porting.** The portable layer compiles
unchanged; the HAL interface is the contract they both agree on.

## Port effort breakdown (realistic estimates)

Assumes the target board has BLE, an IMU, a way to display or print output,
and a buzzer/LED for feedback.

| Layer                           | Stays? | Effort  |
|---------------------------------|--------|---------|
| Portable logic (scoring, math)  | Yes    | 0 days  |
| HAL interface (platform.h)      | Yes    | 0 days  |
| Board driver (new)              | No     | 5–10 days |
| BLE stack quirks                | Varies | 1–3 days |
| IMU drift / calibration tuning  | Varies | 1–2 days |
| Power / sleep mode              | Varies | 1–3 days |
| Enclosure / mounting            | —      | Separate hardware work |
| **Total (firmware only)**       |        | **~2 weeks** |

## Step-by-step porting guide

Assume you're porting from M5StickC Plus to a Seeed XIAO ESP32-S3 (as a
representative target).

### Step 0 — Validate the target has the required capabilities

Check the new board meets these minimums:

- BLE GATT server with custom service UUIDs (any modern MCU supports this)
- 6-axis IMU accessible at ≥50 Hz (built-in or external I2C/SPI)
- Persistent storage for 10 KB of sample data (flash is fine)
- Some output for user feedback: display, LED, buzzer — at least one

If the board lacks any of these, KineticAI can still work but with reduced
functionality. Document what gets dropped.

### Step 1 — Create the board driver stub

```bash
cd firmware/KineticAI_IMU
cp board_m5stickc_plus.h board_<newboard>.h
# Edit board_<newboard>.h — replace M5Unified calls with the new board's SDK
```

Implement the platform.h interface one function at a time. Stub out functions
that can't be implemented yet (e.g., `kai_buzzer_tone(...)` → empty body if
the board has no buzzer).

### Step 2 — Set the board selection

In `KineticAI_IMU.ino`, change the board include:

```cpp
// #include "board_m5stickc_plus.h"
#include "board_<newboard>.h"
```

### Step 3 — Get a hello-world blink

Flash the most minimal version that just initializes the board and blinks
an LED or prints to serial. Confirms your toolchain works.

### Step 4 — Get IMU reading

Wire up `kai_imu_read()` → confirm you see accel + gyro values changing when
you move the board. Use serial monitor. **Don't proceed until IMU is reliable.**

### Step 5 — Get BLE advertising

Implement `kai_ble_init()` → advertise as `KineticAI-L`. Confirm with nRF
Connect on a phone. Include the full service UUID per `PROTOCOL.md`.

### Step 6 — Get motion packet flowing

Implement the 34-byte motion packet notification at 50 Hz. Confirm via nRF
Connect: subscribe to notifications, see hex values changing as you move
the board.

### Step 7 — Everything else

Display, buttons, buzzer, battery, power management. Implement in order of
importance to user experience.

### Step 8 — Full phone-app verification

Run the actual KineticAI Android app against the new board. If the protocol
is correctly implemented, the app should see no difference from the M5.

### Step 9 — Field test

Real skiing day. Check:
- Battery life under real usage
- BLE connection stability in motion
- Cold tolerance (boot down to -15°C is realistic)
- Waterproofing (snow melt on the enclosure)
- Mounting robustness

### Step 10 — Lock the config

Commit the working board driver. Add a row to `HARDWARE_MATRIX.md` with
actual measured battery life, connection stats, and any gotchas discovered.

## Common porting gotchas

Based on the M5 port experience:

- **IRAM overflow** — BLE + WiFi coexistence eats a lot on ESP32 classic.
  Mitigate with NimBLE (not Bluedroid) or split features.
- **ADC driver conflicts** — IDF 5.x broke legacy ADC on several boards. Use
  direct I2C for battery monitoring where possible (as we did for AXP192).
- **Wire / I2C library discovery** — arduino-cli sometimes fails to find the
  Wire library in an older core. Explicit `#include <Wire.h>` and checking
  "Used library" in compile output helps.
- **CRLF in scripts synced via OneDrive/Dropbox** — shell scripts can break.
  Run `sed -i 's/\r$//' *.sh` after every sync.
- **Device name in BLE advertising** — advertising packet is only 31 bytes.
  Name + 128-bit UUID + flags overflows. Put name in primary packet, service
  UUID in scan response (as we do).
- **IMU cache** — some boards' frameworks don't auto-refresh IMU data. Call
  `update()` explicitly before reading.

## Maintaining the HAL interface

The HAL is a contract. Rules for evolving it:

- **Adding new functions is fine** — old boards just don't implement them.
  The portable layer should check for availability via a feature flag.
- **Removing or changing semantics of existing functions is breaking.** Avoid.
- **If you must break the HAL, bump a version macro in platform.h** so boards
  can #ifdef their compatibility.

## Testing multi-board support

Eventually we should CI both the M5 and whatever second board we ship. For
now, one canary device per major board family in the maintainer's test pile
is enough.

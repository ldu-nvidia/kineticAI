# KineticAI Boot Sensor — BLE Wire Protocol

**Stable contract between firmware and phone app. Versioned.**

This document is the authoritative spec for what flows between a boot-mounted
sensor and the KineticAI phone app over BLE. Any hardware that implements this
protocol correctly will work with the phone app with **zero app-side changes**.

## Version history

| Version | Date | Changes |
|---------|------|---------|
| v1.0    | 2026-04 | Initial release: 6-axis IMU + dual IMU + tri-segment + control |

## BLE service

| Role       | UUID                                   |
|------------|----------------------------------------|
| Service    | `4d430001-0000-1000-8000-00805f9b34fb` |

## Characteristics

All UUIDs follow the base pattern `4d4300XX-0000-1000-8000-00805f9b34fb` where
`XX` is the characteristic ID.

| XX | Name             | Access        | Description |
|----|------------------|---------------|-------------|
| 02 | Motion           | Read + Notify | 34-byte merged motion packet at 50 Hz (see below) |
| 03 | Side             | Read          | 1 byte: ASCII `'L'` or `'R'` identifying which boot |
| 04 | Command          | Write         | Phone → sensor commands (see below) |
| 05 | Mic              | Read + Notify | Reserved — snow-type analysis from on-board mic (optional) |
| 06 | Proximity        | Read + Notify | Reserved — nearby-object warnings (optional) |
| 07 | DualIMU          | Read + Notify | Reserved — dual-IMU derived metrics (if external IMU present) |
| 08 | TriSegment       | Read + Notify | Reserved — shell+cuff+shin kinematics (if BNO055 present) |
| 09 | Thermal          | Read + Notify | Reserved — AMG8833 thermal analysis (optional) |

Boards that lack optional sensors may omit characteristics 05–09 or leave them
idle. Core operation requires only `02`, `03`, `04`.

## Motion packet format (char 02)

34 bytes, little-endian where applicable. Sent at 50 Hz (every 20 ms).

| Bytes | Field           | Encoding                              |
|-------|-----------------|---------------------------------------|
| 0–1   | accelX          | int16, scale 208.97 → m/s² (÷ 208.97) |
| 2–3   | accelY          | int16, scale 208.97 → m/s²            |
| 4–5   | accelZ          | int16, scale 208.97 → m/s²            |
| 6–7   | gyroX           | int16, scale 938.88 → rad/s (÷ 938.88)|
| 8–9   | gyroY           | int16, scale 938.88 → rad/s           |
| 10–11 | gyroZ           | int16, scale 938.88 → rad/s           |
| 12–21 | dualImu metrics | 10 bytes, all zero if not available (see DualIMU spec) |
| 22–33 | triSegment      | 12 bytes, all zero if not available (see TriSegment spec) |

### Decoding accelerometer (Kotlin)

```kotlin
val ax = rawInt16 / 208.97f  // m/s²
```

### Decoding gyroscope (Kotlin)

```kotlin
val gx = rawInt16 / 938.88f  // rad/s
```

## Command packet format (char 04)

Phone writes 1–N bytes. First byte is command ID.

| Cmd  | Payload         | Meaning |
|------|-----------------|---------|
| 0x01 | (none)          | Start recording |
| 0x02 | (none)          | Stop recording |
| 0x03 | (none)          | Clear local buffer |
| 0x10 | (1–30 bytes)    | Custom display command (board-specific — see custom_display.h) |
| 0x30 | 1 byte: 0/1/2   | Set power state: 0=SKIING, 1=LIFT, 2=SLEEP |
| 0x31 | 2 bytes: freq16, 1 byte: duration/10ms | Play tone |
| 0x32 | 1 byte          | Set LCD brightness (0–255) |
| 0x33 | (none)          | Force wake from sleep |

## Advertising

- Device name: `KineticAI-L` or `KineticAI-R` (matches side)
- Advertisement packet: flags + name (11–13 bytes)
- Scan response: complete service UUID (18 bytes)

This split is required because a 128-bit UUID + full name + flags exceeds the
31-byte advertising packet limit. Scanners will see the name immediately and
can filter by service UUID via scan response.

## Connection parameters

- Preferred connection interval: 7.5 ms – 22.5 ms (BLE params `0x06` – `0x12`)
- MTU: default (23). Do not require extended MTU — some hardware variants
  don't support it reliably.

## Protocol versioning contract

- **Additive changes** (new characteristics, new command IDs) are non-breaking.
  App should tolerate unknown characteristics and unknown commands.
- **Field meaning changes** (different scaling, different byte layout in an
  existing field) require a **major version bump** and a new UUID for the
  affected characteristic.
- **Firmware MUST advertise a version byte** (planned for v1.1) on a reserved
  characteristic so the app can negotiate compatibility.

## Implementing this protocol on new hardware

1. Use a BLE library that supports custom GATT services (e.g., NimBLE on ESP32,
   Zephyr on Nordic, ArduinoBLE on certain Arduinos).
2. Create the service with UUID above.
3. Add the 8 characteristics with the UUIDs, properties, and data formats
   listed. Optional characteristics may be omitted but existing ones must
   match exactly.
4. Advertise as described, with correct `-L` / `-R` naming.
5. Respond to command writes as described.

If the phone app sees the service UUID, the name pattern, and the motion
packet arriving at 50 Hz with the correct packing, it will treat the device
as a KineticAI-compatible boot sensor regardless of underlying hardware.

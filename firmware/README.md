# KineticAI Boot IMU Firmware

Arduino firmware for M5StickC Plus2 boot sensors.

## What It Does

**While skiing (BLE mode):**
- Streams 6-axis IMU at 50 Hz to phone via BLE for real-time coaching
- Simultaneously records full 200 Hz IMU data to local memory
- Buzzer beeps on each turn (pitch = edge angle quality)
- Screen shows live edge angle, turn count, battery
- Button B marks special moments (timestamps stored)

**After skiing (WiFi mode):**
- Press Button A to switch to WiFi transfer mode
- Connects to your phone hotspot or lodge WiFi
- Serves recorded 200 Hz data over HTTP for bulk download
- Phone app downloads ~53,000 records in ~30 seconds
- Then clears memory and switches back to BLE mode

## Setup

1. Install [Arduino IDE](https://www.arduino.cc/en/software)
2. Add ESP32 board: File → Preferences → Board Manager URLs, add:
   `https://m5stack.oss-cn-shenzhen.aliyuncs.com/resource/arduino/package_m5stack_index.json`
3. Install board: Tools → Board Manager → search "M5Stack" → install
4. Install library: Tools → Manage Libraries → search "M5StickCPlus2" → install
5. Select board: Tools → Board → M5Stack → M5StickC-Plus2

## Flashing

### Left boot:
1. Open `KineticAI_IMU/KineticAI_IMU.ino`
2. Ensure line 34 reads: `#define BOOT_SIDE 'L'`
3. Optionally update WiFi credentials on lines 38-39
4. Connect via USB-C, click Upload

### Right boot:
1. Change line 34 to: `#define BOOT_SIDE 'R'`
2. Connect the other M5StickC, click Upload

## WiFi Setup

Set your phone hotspot name and password on these lines:
```cpp
#define WIFI_SSID "KineticAI-Hotspot"
#define WIFI_PASS "kineticai2026"
```

On your Galaxy S26 Ultra, create a WiFi hotspot with matching credentials.

## Buttons

| Button | Location | Action |
|--------|----------|--------|
| A | Front face | Toggle WiFi transfer mode |
| B | Side | Mark a moment (buzzer confirms) |
| C | Top edge | Power on (2s) / Power off (6s) |

## BLE Protocol

| UUID | Type | Description |
|------|------|-------------|
| `4d430001-...` | Service | KineticAI IMU Service |
| `4d430002-...` | Notify | 24 bytes: 6 × float32 at 50 Hz |
| `4d430003-...` | Read | 1 byte: 'L' or 'R' |
| `4d430004-...` | Write | Commands: 0x01=start rec, 0x02=stop rec, 0x03=clear |

## WiFi Transfer Protocol

When in WiFi mode (Button A), the M5 serves HTTP on port 80:

| Endpoint | Method | Response |
|----------|--------|----------|
| `/status` | GET | JSON: side, record count, battery, uptime |
| `/data` | GET | Binary: 8-byte header + N × 28-byte records |
| `/markers` | GET | JSON array of marker timestamps (ms) |
| `/clear` | GET | Clears all recorded data |

### Binary data format:
- Header: 1 byte side + 3 bytes padding + 4 bytes record count (uint32 LE)
- Each record: 4 bytes timestamp (uint32 LE) + 6 × 4 bytes float32 LE (accel XYZ, gyro XYZ)
- Units: accel in m/s², gyro in rad/s

## Capacity

| Metric | Value |
|--------|-------|
| BLE stream rate | 50 Hz (1,200 bytes/sec) |
| Local record rate | 200 Hz (5,600 bytes/sec) |
| Buffer capacity | ~53,000 records (~4.5 min at 200 Hz) |
| Buffer behavior | Circular — keeps most recent data when full |
| WiFi transfer speed | ~30 seconds for full buffer |

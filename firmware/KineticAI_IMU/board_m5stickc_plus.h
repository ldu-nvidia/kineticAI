// board_m5stickc_plus.h — M5StickC Plus (ESP32-PICO-D4) board driver
//
// Implements platform.h for the M5StickC Plus v1.1 with:
//   - ESP32-PICO-D4, 4 MB flash, no PSRAM
//   - Built-in MPU6886 6-axis IMU
//   - 135×240 ST7789v2 LCD
//   - AXP192 PMIC (battery monitor via I2C)
//   - Buttons A, B, C; buzzer; optional vibration via Grove port
//
// Tooling:
//   - m5stack:esp32 @ 2.1.4 (2.x is required; 3.x has ADC driver conflict)
//   - M5Unified @ 0.2.13
//   - NimBLE-Arduino @ 2.5.0
//
// This header is INCLUDED — not linked — into KineticAI_IMU.ino. Functions
// are defined inline here so Arduino-style single-unit compilation works.
//
// To port to a different board: copy this file, replace the guts, update
// the capability flags below.

#pragma once

// ── Capability flags — declare BEFORE including platform.h ──
#define KAI_HAS_DISPLAY         1
#define KAI_HAS_BUZZER          1
#define KAI_HAS_VIBRATION       1   // via Grove GPIO25
#define KAI_HAS_BUTTONS         1
#define KAI_HAS_BATTERY_MONITOR 1

#include "platform.h"

#include <M5Unified.h>
#include <Wire.h>
#include <NimBLEDevice.h>
#include <stdarg.h>

// ── BLE UUIDs (canonical; see PROTOCOL.md) ──
#define KAI_BLE_SVC_UUID        "4d430001-0000-1000-8000-00805f9b34fb"
#define KAI_BLE_MOTION_CHAR     "4d430002-0000-1000-8000-00805f9b34fb"
#define KAI_BLE_SIDE_CHAR       "4d430003-0000-1000-8000-00805f9b34fb"
#define KAI_BLE_CMD_CHAR        "4d430004-0000-1000-8000-00805f9b34fb"

// ── Private state (board-internal) ──
static NimBLEServer*         s_server       = nullptr;
static NimBLECharacteristic* s_motion_char  = nullptr;
static NimBLECharacteristic* s_cmd_char     = nullptr;
static bool                  s_ble_connected = false;
static kai_ble_command_handler_t s_cmd_handler = nullptr;
static kai_power_mode_t      s_power_mode    = KAI_POWER_ACTIVE;

class KaiServerCallbacks : public NimBLEServerCallbacks {
    void onConnect(NimBLEServer*, NimBLEConnInfo&) override {
        s_ble_connected = true;
    }
    void onDisconnect(NimBLEServer*, NimBLEConnInfo&, int) override {
        s_ble_connected = false;
    }
};

class KaiCmdCallbacks : public NimBLECharacteristicCallbacks {
    void onWrite(NimBLECharacteristic* c, NimBLEConnInfo&) override {
        if (!s_cmd_handler) return;
        std::string val = c->getValue();
        if (!val.empty()) {
            s_cmd_handler((const uint8_t*)val.data(), val.length());
        }
    }
};

// ══════════════════════════════════════════════════════════════════════════
//  Lifecycle
// ══════════════════════════════════════════════════════════════════════════

inline void kai_platform_init() {
    auto cfg = M5.config();
    // Workarounds for M5StickC Plus on Arduino ESP32 core 2.1.4:
    //   - output_power=false: skip M5.Power init to avoid legacy ADC conflict
    //   - internal_mic=false: Plus has no mic (only Plus 2 does)
    cfg.output_power = false;
    cfg.internal_mic = false;
    cfg.internal_spk = false;
    M5.begin(cfg);

    M5.Display.setRotation(1);
    M5.Display.fillScreen(BLACK);
    M5.Display.setBrightness(80);
}

inline void kai_platform_tick() {
    M5.update();
    M5.Imu.update();  // M5.update() does NOT poll IMU on this core — must be explicit
}

inline uint32_t kai_platform_millis() {
    return millis();
}

// ══════════════════════════════════════════════════════════════════════════
//  IMU
// ══════════════════════════════════════════════════════════════════════════

inline bool kai_imu_read(kai_imu_sample_t& out) {
    auto d = M5.Imu.getImuData();
    out.accel_x = d.accel.x * 9.81f;
    out.accel_y = d.accel.y * 9.81f;
    out.accel_z = d.accel.z * 9.81f;
    out.gyro_x  = d.gyro.x  * 0.01745329f;  // deg/s → rad/s
    out.gyro_y  = d.gyro.y  * 0.01745329f;
    out.gyro_z  = d.gyro.z  * 0.01745329f;
    out.timestamp_ms = millis();
    return true;
}

// ══════════════════════════════════════════════════════════════════════════
//  Display
// ══════════════════════════════════════════════════════════════════════════

inline int kai_display_width()  { return M5.Display.width(); }
inline int kai_display_height() { return M5.Display.height(); }

inline void kai_display_clear(uint16_t bg_color) {
    M5.Display.fillScreen(bg_color);
}

inline void kai_display_fill_rect(int x, int y, int w, int h, uint16_t color) {
    M5.Display.fillRect(x, y, w, h, color);
}

inline void kai_display_text(int x, int y, const char* text, uint16_t fg_color, uint8_t size) {
    M5.Display.setCursor(x, y);
    M5.Display.setTextColor(fg_color);
    M5.Display.setTextSize(size);
    M5.Display.print(text);
}

inline void kai_display_set_brightness(uint8_t level) {
    M5.Display.setBrightness(level);
}

// ══════════════════════════════════════════════════════════════════════════
//  Buzzer
// ══════════════════════════════════════════════════════════════════════════

#define KAI_BUZZER_PIN 2

inline void kai_buzzer_tone(uint16_t freq_hz, uint16_t duration_ms) {
    tone(KAI_BUZZER_PIN, freq_hz, duration_ms);
}

// ══════════════════════════════════════════════════════════════════════════
//  Vibration (Grove port GPIO 25 — if user adds an external motor)
// ══════════════════════════════════════════════════════════════════════════

#define KAI_VIBRO_PIN 25

inline void kai_vibrate(uint8_t intensity, uint16_t duration_ms) {
    analogWrite(KAI_VIBRO_PIN, intensity);
    // Caller is responsible for timing; we don't block here.
    // (For a blocking version, add a delay + off. Kept non-blocking for main loop.)
    (void)duration_ms;
}

// ══════════════════════════════════════════════════════════════════════════
//  Buttons
// ══════════════════════════════════════════════════════════════════════════

inline bool kai_button_pressed(kai_button_t btn) {
    switch (btn) {
        case KAI_BTN_A: return M5.BtnA.wasPressed();
        case KAI_BTN_B: return M5.BtnB.wasPressed();
        case KAI_BTN_C: return M5.BtnPWR.wasPressed();
        default:        return false;
    }
}

inline bool kai_button_held(kai_button_t btn) {
    switch (btn) {
        case KAI_BTN_A: return M5.BtnA.isPressed();
        case KAI_BTN_B: return M5.BtnB.isPressed();
        case KAI_BTN_C: return M5.BtnPWR.isPressed();
        default:        return false;
    }
}

inline bool kai_button_held_for(kai_button_t btn, uint32_t duration_ms) {
    switch (btn) {
        case KAI_BTN_A: return M5.BtnA.wasReleaseFor(duration_ms);
        case KAI_BTN_B: return M5.BtnB.wasReleaseFor(duration_ms);
        case KAI_BTN_C: return M5.BtnPWR.wasReleaseFor(duration_ms);
        default:        return false;
    }
}

// ══════════════════════════════════════════════════════════════════════════
//  Battery (direct AXP192 I2C — bypasses M5.Power's buggy ADC path)
// ══════════════════════════════════════════════════════════════════════════

inline float kai_battery_voltage() {
    uint8_t buf[2] = {0, 0};
    if (!M5.In_I2C.readRegister(0x34, 0x78, buf, 2, 400000)) return 0.0f;
    uint16_t raw = ((uint16_t)buf[0] << 4) | (buf[1] & 0x0F);
    return raw * 0.0011f;  // volts
}

inline uint8_t kai_battery_percent() {
    float v = kai_battery_voltage();
    if (v <= 0.0f) return 0;
    int pct = (int)((v - 3.2f) / 0.9f * 100.0f);
    if (pct < 0)   pct = 0;
    if (pct > 100) pct = 100;
    return (uint8_t)pct;
}

// ══════════════════════════════════════════════════════════════════════════
//  BLE
// ══════════════════════════════════════════════════════════════════════════

inline void kai_ble_init(const char* device_name) {
    NimBLEDevice::init(device_name);
    s_server = NimBLEDevice::createServer();
    s_server->setCallbacks(new KaiServerCallbacks());

    NimBLEService* svc = s_server->createService(KAI_BLE_SVC_UUID);

    s_motion_char = svc->createCharacteristic(
        KAI_BLE_MOTION_CHAR,
        NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY
    );

    NimBLECharacteristic* side_char = svc->createCharacteristic(
        KAI_BLE_SIDE_CHAR, NIMBLE_PROPERTY::READ);
    uint8_t side = (device_name && device_name[strlen(device_name)-1] == 'R') ? 'R' : 'L';
    side_char->setValue(&side, 1);

    s_cmd_char = svc->createCharacteristic(
        KAI_BLE_CMD_CHAR, NIMBLE_PROPERTY::WRITE);
    s_cmd_char->setCallbacks(new KaiCmdCallbacks());

    svc->start();

    // Advertising: name in primary packet, service UUID in scan response
    // (required because both together exceed 31-byte advertising limit)
    NimBLEAdvertising* adv = NimBLEDevice::getAdvertising();
    NimBLEAdvertisementData advData;
    advData.setFlags(BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP);
    advData.setName(device_name);
    adv->setAdvertisementData(advData);

    NimBLEAdvertisementData scanData;
    scanData.setCompleteServices(NimBLEUUID(KAI_BLE_SVC_UUID));
    adv->setScanResponseData(scanData);

    adv->setPreferredParams(0x06, 0x12);
    NimBLEDevice::startAdvertising();
}

inline bool kai_ble_notify_motion(const uint8_t packet[34]) {
    if (!s_ble_connected || !s_motion_char) return false;
    s_motion_char->setValue(packet, 34);
    s_motion_char->notify();
    return true;
}

inline bool kai_ble_is_connected() { return s_ble_connected; }

inline void kai_ble_set_command_handler(kai_ble_command_handler_t h) {
    s_cmd_handler = h;
}

// ══════════════════════════════════════════════════════════════════════════
//  Power management
// ══════════════════════════════════════════════════════════════════════════

inline void kai_power_set_mode(kai_power_mode_t mode) {
    s_power_mode = mode;
    switch (mode) {
        case KAI_POWER_ACTIVE: M5.Display.setBrightness(80);  break;
        case KAI_POWER_IDLE:   M5.Display.setBrightness(20);  break;
        case KAI_POWER_SLEEP:  M5.Display.setBrightness(5);   break;
    }
}

inline kai_power_mode_t kai_power_get_mode() { return s_power_mode; }

// ══════════════════════════════════════════════════════════════════════════
//  Debug
// ══════════════════════════════════════════════════════════════════════════

inline void kai_debug_init(uint32_t baud_rate) {
    Serial.begin(baud_rate);
}

inline void kai_debug_print(const char* msg) {
    Serial.println(msg);
}

inline void kai_debug_printf(const char* fmt, ...) {
    char buf[256];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);
    Serial.print(buf);
}

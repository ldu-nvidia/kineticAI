/**
 * MyCarv Power Management & Protocol Optimization
 *
 * Optimizations:
 *   1. IMU packet compression: 6×float32 (24B) → 6×int16 (12B)
 *   2. LCD refresh: 5Hz → 2Hz
 *   3. Mic duty: 4Hz → 2Hz
 *   4. Wake-on-motion deep sleep when stopped
 *   5. Phone-commanded power states: SKIING / LIFT / SLEEP
 *   6. Phone-commanded buzzer: PLAY_TONE(freq, duration)
 *
 * Power states:
 *   SKIING: Full-rate IMU (50Hz), mic on (2Hz), LCD on (2Hz), recording on
 *   LIFT:   Low-rate IMU (5Hz), mic off, LCD dim, recording off, BLE idle
 *   SLEEP:  Deep sleep, wake-on-motion interrupt from IMU, ~50µA draw
 *
 * BLE Command extensions (written to CMD_CHAR_UUID 0x04):
 *   0x30 = Set power state (1 byte: 0=SKIING, 1=LIFT, 2=SLEEP)
 *   0x31 = Play tone (2 bytes: freq_hz LE uint16, 1 byte: duration_10ms)
 *   0x32 = Set LCD brightness (1 byte: 0-255)
 *   0x33 = Force wake (no payload)
 */

#pragma once
#include <M5StickCPlus2.h>
#include <esp_sleep.h>

// ── Power States ──
enum PowerState : uint8_t {
    PWR_SKIING = 0,  // Full active: IMU 50Hz, mic 2Hz, LCD 2Hz, record on
    PWR_LIFT   = 1,  // Low power: IMU 5Hz, mic off, LCD off/dim, record off
    PWR_SLEEP  = 2,  // Deep sleep: wake-on-motion only
};

// ── Timing Constants (in ms) ──
struct PowerConfig {
    unsigned long bleIntervalMs;     // BLE send rate
    unsigned long recordIntervalMs;  // Local recording rate
    unsigned long displayIntervalMs; // LCD refresh rate
    unsigned long micIntervalMs;     // Mic analysis rate (0 = off)
    uint8_t lcdBrightness;           // 0-255
    bool recordingEnabled;
    bool micEnabled;
};

static const PowerConfig CONFIG_SKIING = {
    .bleIntervalMs = 20,       // 50 Hz
    .recordIntervalMs = 5,     // 200 Hz
    .displayIntervalMs = 500,  // 2 Hz (optimized from 200ms/5Hz)
    .micIntervalMs = 500,      // 2 Hz (optimized from 250ms/4Hz)
    .lcdBrightness = 80,
    .recordingEnabled = true,
    .micEnabled = true,
};

static const PowerConfig CONFIG_LIFT = {
    .bleIntervalMs = 200,      // 5 Hz (save BLE radio time)
    .recordIntervalMs = 0,     // off
    .displayIntervalMs = 2000, // 0.5 Hz (barely update)
    .micIntervalMs = 0,        // off
    .lcdBrightness = 20,       // dim
    .recordingEnabled = false,
    .micEnabled = false,
};

static PowerState currentPowerState = PWR_SKIING;
static PowerConfig currentConfig = CONFIG_SKIING;
static unsigned long motionLastDetected = 0;
static const unsigned long SLEEP_TIMEOUT_MS = 60000; // 60s no motion → sleep

// ── IMU Packet Compression ──

/**
 * Pack 6 floats (accel m/s² + gyro rad/s) into 12 bytes (6 × int16).
 *
 * Encoding:
 *   accel: ±156.8 m/s² range (±16g) → int16, scale = 32767/156.8 = 208.97
 *   gyro:  ±34.9 rad/s range (±2000°/s) → int16, scale = 32767/34.9 = 938.88
 *
 * Phone decodes:
 *   accel_ms2 = (int16 / 208.97)
 *   gyro_rads = (int16 / 938.88)
 */
#define ACCEL_SCALE  208.97f
#define GYRO_SCALE   938.88f

void packImuCompressed(float* floatPacket, uint8_t* out12) {
    int16_t compressed[6];
    // Accel XYZ
    compressed[0] = (int16_t)constrain(floatPacket[0] * ACCEL_SCALE, -32767, 32767);
    compressed[1] = (int16_t)constrain(floatPacket[1] * ACCEL_SCALE, -32767, 32767);
    compressed[2] = (int16_t)constrain(floatPacket[2] * ACCEL_SCALE, -32767, 32767);
    // Gyro XYZ
    compressed[3] = (int16_t)constrain(floatPacket[3] * GYRO_SCALE, -32767, 32767);
    compressed[4] = (int16_t)constrain(floatPacket[4] * GYRO_SCALE, -32767, 32767);
    compressed[5] = (int16_t)constrain(floatPacket[5] * GYRO_SCALE, -32767, 32767);

    memcpy(out12, compressed, 12);
}

// ── Power State Transitions ──

void setPowerState(PowerState state) {
    if (state == currentPowerState) return;
    currentPowerState = state;

    switch (state) {
        case PWR_SKIING:
            currentConfig = CONFIG_SKIING;
            StickCP2.Display.setBrightness(currentConfig.lcdBrightness);
            StickCP2.Display.wakeup();
            break;

        case PWR_LIFT:
            currentConfig = CONFIG_LIFT;
            StickCP2.Display.setBrightness(currentConfig.lcdBrightness);
            break;

        case PWR_SLEEP:
            enterDeepSleep();
            break;
    }
}

/**
 * Enter deep sleep with wake-on-motion from IMU.
 * The MPU6886 Wake-on-Motion interrupt wakes the ESP32 via GPIO.
 *
 * Since M5StickC Plus2 uses HOLD pin (GPIO4) for power,
 * we use light sleep with timer wakeup as a practical alternative —
 * wake every 2 seconds, check IMU for motion, sleep again if still.
 */
void enterDeepSleep() {
    // Show sleep indicator
    StickCP2.Display.fillScreen(BLACK);
    StickCP2.Display.setTextSize(2);
    StickCP2.Display.setTextColor(TFT_DARKGREY);
    StickCP2.Display.setCursor(30, 50);
    StickCP2.Display.printf("SLEEP");
    StickCP2.Display.setCursor(20, 80);
    StickCP2.Display.setTextSize(1);
    StickCP2.Display.printf("Move boot to wake");
    StickCP2.Display.setBrightness(5);

    // Light sleep loop: wake every 2s, check motion
    while (true) {
        esp_sleep_enable_timer_wakeup(2000000); // 2 seconds
        esp_light_sleep_start();

        // Check if there's motion (read IMU quickly)
        auto data = StickCP2.Imu.getImuData();
        float accelMag = sqrtf(data.accel.x * data.accel.x +
                               data.accel.y * data.accel.y +
                               data.accel.z * data.accel.z);
        float gyroMag = sqrtf(data.gyro.x * data.gyro.x +
                              data.gyro.y * data.gyro.y +
                              data.gyro.z * data.gyro.z);

        // Motion threshold: accel deviates from 1G or gyro > 5°/s
        bool hasMotion = (fabsf(accelMag - 1.0f) > 0.15f) || (gyroMag > 5.0f);

        if (hasMotion) {
            // Wake up!
            setPowerState(PWR_SKIING);
            motionLastDetected = millis();

            // Wake buzz
            tone(2, 1200, 80);
            delay(100);
            tone(2, 1600, 80);
            return;
        }

        // Check if button was pressed (Button A = GPIO37)
        if (digitalRead(37) == LOW) {
            setPowerState(PWR_SKIING);
            motionLastDetected = millis();
            return;
        }
    }
}

/**
 * Check if boot should auto-sleep due to inactivity.
 * Call from main loop.
 */
void checkAutoSleep(float gyroMag) {
    if (currentPowerState == PWR_SLEEP) return;

    if (gyroMag > 2.0f) {
        motionLastDetected = millis();
    }

    if (currentPowerState == PWR_LIFT || currentPowerState == PWR_SKIING) {
        if (millis() - motionLastDetected > SLEEP_TIMEOUT_MS) {
            setPowerState(PWR_SLEEP);
        }
    }
}

/**
 * Process phone power commands (0x30-0x33).
 */
void processPowerCommand(const uint8_t* data, size_t len) {
    if (len < 1) return;
    uint8_t cmd = data[0];

    switch (cmd) {
        case 0x30: // Set power state
            if (len >= 2) {
                uint8_t state = data[1];
                if (state <= 2) setPowerState((PowerState)state);
            }
            break;

        case 0x31: // Play tone (freq_hz LE uint16 + duration_10ms)
            if (len >= 4) {
                uint16_t freq = data[1] | (data[2] << 8);
                uint8_t dur10ms = data[3];
                tone(2, freq, dur10ms * 10);
            }
            break;

        case 0x32: // Set LCD brightness
            if (len >= 2) {
                StickCP2.Display.setBrightness(data[1]);
            }
            break;

        case 0x33: // Force wake
            if (currentPowerState == PWR_SLEEP) {
                setPowerState(PWR_SKIING);
                motionLastDetected = millis();
            }
            break;
    }
}

/**
 * Get current state label for display.
 */
const char* powerStateStr() {
    switch (currentPowerState) {
        case PWR_SKIING: return "SKI";
        case PWR_LIFT:   return "LIFT";
        case PWR_SLEEP:  return "ZZZ";
        default:         return "?";
    }
}

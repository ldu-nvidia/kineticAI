/**
 * KineticAI Proximity Warning System
 *
 * Uses LD2410 24GHz mmWave radar for rear-facing collision detection.
 * Detects approaching skiers/objects and triggers multi-level warnings.
 *
 * LD2410 Features:
 *   - 24GHz FMCW radar, 0.75m – 6m detection range
 *   - Distinguishes moving vs stationary targets (ignores trees, poles)
 *   - Reports distance and energy level for both target types
 *   - UART interface at 256000 baud
 *   - ~70mA active, can be duty-cycled
 *
 * Connection to M5StickC Plus2:
 *   LD2410 TX → GPIO33 (via Grove port or direct wire)
 *   LD2410 RX → GPIO32
 *   LD2410 VCC → 5V (from Grove port)
 *   LD2410 GND → GND
 *
 * Warning Levels:
 *   CLEAR:   No target or target not closing  → no action
 *   CAUTION: Target 4-6m, closing            → LED yellow blink
 *   WARNING: Target 2-4m, closing fast        → LED red flash + gentle vibration
 *   DANGER:  Target <2m, closing              → LED solid red + strong vibration + buzzer
 */

#pragma once
#include <HardwareSerial.h>

// ── LD2410 UART Config ──
#define PROX_UART_NUM    1
#define PROX_RX_PIN      33
#define PROX_TX_PIN      32
#define PROX_BAUD        256000

// ── Warning Levels ──
enum ProximityLevel : uint8_t {
    PROX_CLEAR   = 0,
    PROX_CAUTION = 1,  // 4-6m, closing
    PROX_WARNING = 2,  // 2-4m, closing fast
    PROX_DANGER  = 3,  // <2m
};

// ── Detection Result ──
struct ProximityResult {
    bool targetDetected;
    uint16_t distanceCm;       // Distance to nearest moving target
    uint8_t energy;            // Target signal energy (0-100)
    float closingSpeedCmPerS;  // Positive = approaching
    ProximityLevel level;
    bool sensorPresent;        // Whether LD2410 is connected
};

static ProximityResult proxResult = {};
static bool proxInitialized = false;

// Distance history for closing speed calculation
#define PROX_HISTORY_SIZE 5
static uint16_t proxDistHistory[PROX_HISTORY_SIZE] = {0};
static unsigned long proxTimeHistory[PROX_HISTORY_SIZE] = {0};
static uint8_t proxHistIdx = 0;
static unsigned long lastProxRead = 0;

// LD2410 frame parsing state
static uint8_t proxFrameBuf[64];
static uint8_t proxFrameIdx = 0;
static bool proxFrameStarted = false;

// Duty cycling: only read every N ms to save power
#define PROX_READ_INTERVAL_MS  100  // 10 Hz

// ── LD2410 Frame Protocol ──
// Header: F4 F3 F2 F1
// Data: varies by report type
// Footer: F8 F7 F6 F5
static const uint8_t FRAME_HEADER[] = {0xF4, 0xF3, 0xF2, 0xF1};
static const uint8_t FRAME_FOOTER[] = {0xF8, 0xF7, 0xF6, 0xF5};

/**
 * Initialize the LD2410 radar sensor on UART.
 */
void proximityInit() {
    Serial1.begin(PROX_BAUD, SERIAL_8N1, PROX_RX_PIN, PROX_TX_PIN);
    delay(100);

    // Check if sensor responds by waiting for any data
    unsigned long start = millis();
    while (millis() - start < 500) {
        if (Serial1.available()) {
            proxResult.sensorPresent = true;
            proxInitialized = true;
            // Flush initial data
            while (Serial1.available()) Serial1.read();
            return;
        }
        delay(10);
    }

    proxResult.sensorPresent = false;
    proxInitialized = false;
}

/**
 * Parse one byte from the LD2410 UART stream.
 * Returns true when a complete frame is parsed.
 *
 * LD2410 engineering mode data frame:
 *   Byte 0-3:   Header (F4 F3 F2 F1)
 *   Byte 4-5:   Data length (little-endian)
 *   Byte 6:     Data type (0x02 = target data, 0x01 = engineering)
 *   Byte 7:     Head byte (0xAA)
 *   Byte 8:     Target status (0=no target, 1=moving, 2=stationary, 3=both)
 *   Byte 9-10:  Moving target distance (cm, LE)
 *   Byte 11:    Moving target energy (0-100)
 *   Byte 12-13: Stationary target distance (cm, LE)
 *   Byte 14:    Stationary target energy (0-100)
 *   Byte 15-16: Detection distance (cm, LE)
 *   ...
 *   Footer:     F8 F7 F6 F5
 */
bool parseProxByte(uint8_t b) {
    if (!proxFrameStarted) {
        // Look for header
        static uint8_t headerMatch = 0;
        if (b == FRAME_HEADER[headerMatch]) {
            headerMatch++;
            if (headerMatch == 4) {
                proxFrameStarted = true;
                proxFrameIdx = 0;
                headerMatch = 0;
            }
        } else {
            headerMatch = 0;
        }
        return false;
    }

    proxFrameBuf[proxFrameIdx++] = b;

    // Check for footer
    if (proxFrameIdx >= 4) {
        uint8_t* tail = proxFrameBuf + proxFrameIdx - 4;
        if (tail[0] == FRAME_FOOTER[0] && tail[1] == FRAME_FOOTER[1] &&
            tail[2] == FRAME_FOOTER[2] && tail[3] == FRAME_FOOTER[3]) {
            proxFrameStarted = false;
            return true; // Complete frame received
        }
    }

    // Overflow protection
    if (proxFrameIdx >= 60) {
        proxFrameStarted = false;
        proxFrameIdx = 0;
    }

    return false;
}

/**
 * Extract target data from a parsed frame.
 */
void extractTargetData() {
    if (proxFrameIdx < 13) return;

    // Byte 2: data type (0x02 = basic target data)
    // Byte 3: 0xAA header
    // Byte 4: target status
    uint8_t status = proxFrameBuf[4];

    if (status == 0) {
        // No target
        proxResult.targetDetected = false;
        proxResult.distanceCm = 0;
        proxResult.energy = 0;
        return;
    }

    // Moving target data (we care about this most)
    if (status == 1 || status == 3) {
        proxResult.targetDetected = true;
        proxResult.distanceCm = proxFrameBuf[5] | (proxFrameBuf[6] << 8);
        proxResult.energy = proxFrameBuf[7];
    } else if (status == 2) {
        // Stationary target only — less interesting for collision detection
        // but still report it
        proxResult.targetDetected = true;
        proxResult.distanceCm = proxFrameBuf[8] | (proxFrameBuf[9] << 8);
        proxResult.energy = proxFrameBuf[10];
    }
}

/**
 * Compute closing speed from distance history.
 * Positive = target getting closer (approaching).
 */
float computeClosingSpeed() {
    // Need at least 2 data points
    uint8_t prevIdx = (proxHistIdx + PROX_HISTORY_SIZE - 1) % PROX_HISTORY_SIZE;
    uint16_t prevDist = proxDistHistory[prevIdx];
    unsigned long prevTime = proxTimeHistory[prevIdx];
    uint16_t currDist = proxResult.distanceCm;
    unsigned long currTime = millis();

    if (prevTime == 0 || prevDist == 0) return 0;

    float dt = (currTime - prevTime) / 1000.0f; // seconds
    if (dt < 0.01f) return 0;

    // Positive closing speed means distance is decreasing (approaching)
    return (float)(prevDist - currDist) / dt;
}

/**
 * Classify warning level from distance and closing speed.
 */
ProximityLevel classifyProximity(uint16_t distCm, float closingCmPerS) {
    if (!proxResult.targetDetected || distCm == 0) return PROX_CLEAR;

    // Not closing (moving away or parallel) → clear
    if (closingCmPerS < 20.0f) return PROX_CLEAR;

    // Distance-based levels, modified by closing speed
    if (distCm < 200) {
        return PROX_DANGER;
    } else if (distCm < 400) {
        return closingCmPerS > 100.0f ? PROX_DANGER : PROX_WARNING;
    } else if (distCm < 600) {
        return closingCmPerS > 200.0f ? PROX_WARNING : PROX_CAUTION;
    }

    return PROX_CLEAR;
}

/**
 * Main proximity read function. Call from main loop.
 * Returns true if new data was processed.
 */
bool readProximity() {
    if (!proxInitialized) return false;

    unsigned long now = millis();
    if (now - lastProxRead < PROX_READ_INTERVAL_MS) return false;
    lastProxRead = now;

    // Read all available bytes from UART
    bool gotFrame = false;
    while (Serial1.available()) {
        uint8_t b = Serial1.read();
        if (parseProxByte(b)) {
            extractTargetData();
            gotFrame = true;
        }
    }

    if (!gotFrame) return false;

    // Compute closing speed
    proxResult.closingSpeedCmPerS = computeClosingSpeed();

    // Store in history
    proxDistHistory[proxHistIdx] = proxResult.distanceCm;
    proxTimeHistory[proxHistIdx] = now;
    proxHistIdx = (proxHistIdx + 1) % PROX_HISTORY_SIZE;

    // Classify warning level
    proxResult.level = classifyProximity(proxResult.distanceCm, proxResult.closingSpeedCmPerS);

    return true;
}

/**
 * Execute warning actions based on proximity level.
 * Controls LED, buzzer, and vibration motor.
 *
 * @param buzzerPin  GPIO for buzzer (2 on M5StickC Plus2)
 * @param ledPin     GPIO for LED (19 on M5StickC Plus2)
 * @param vibroPin   GPIO for vibration motor (if connected, else -1)
 */
void executeProximityWarning(int buzzerPin, int ledPin, int vibroPin) {
    static unsigned long lastWarningAction = 0;
    unsigned long now = millis();

    switch (proxResult.level) {
        case PROX_CLEAR:
            // No action — LED/vibro controlled by other systems
            break;

        case PROX_CAUTION:
            // Yellow blink: LED on 100ms every 500ms
            if ((now / 500) % 2 == 0 && (now % 500) < 100) {
                digitalWrite(ledPin, HIGH);
            } else if (proxResult.level == PROX_CAUTION) {
                // Don't override other LED patterns unless we're still in caution
            }
            break;

        case PROX_WARNING:
            // Red rapid flash + gentle vibration
            digitalWrite(ledPin, (now / 150) % 2 ? HIGH : LOW);
            if (vibroPin >= 0) {
                // Pulse vibration
                analogWrite(vibroPin, (now / 200) % 2 ? 128 : 0);
            }
            break;

        case PROX_DANGER:
            // Solid red + strong vibration + buzzer
            digitalWrite(ledPin, HIGH);
            if (vibroPin >= 0) {
                analogWrite(vibroPin, 255); // Full vibration
            }
            // Buzzer alarm: alternating tones
            if (now - lastWarningAction > 300) {
                lastWarningAction = now;
                int freq = (now / 300) % 2 ? 2000 : 1500;
                tone(buzzerPin, freq, 200);
            }
            break;
    }

    // Auto-reset vibration when clear
    if (proxResult.level < PROX_WARNING && vibroPin >= 0) {
        analogWrite(vibroPin, 0);
    }
}

/**
 * Pack proximity data into 4 bytes for BLE transmission.
 *
 * Byte 0: Warning level (0-3)
 * Byte 1: Distance high byte (cm / 4, max ~1020cm)
 * Byte 2: Closing speed (cm/s / 4, capped at 255 = 1020 cm/s)
 * Byte 3: Energy (0-100)
 */
void packProximityResult(uint8_t* out) {
    out[0] = proxResult.level;
    out[1] = (uint8_t)min(255, (int)(proxResult.distanceCm / 4));
    out[2] = (uint8_t)min(255, (int)(proxResult.closingSpeedCmPerS / 4.0f));
    out[3] = proxResult.energy;
}

/**
 * Get display string for proximity level.
 */
const char* proximityLevelStr(ProximityLevel level) {
    switch (level) {
        case PROX_CAUTION: return "CAUTION";
        case PROX_WARNING: return "WARNING";
        case PROX_DANGER:  return "DANGER!";
        default:           return "";
    }
}

uint16_t proximityLevelColor(ProximityLevel level) {
    switch (level) {
        case PROX_CAUTION: return 0xFFE0; // Yellow
        case PROX_WARNING: return 0xFD20; // Orange
        case PROX_DANGER:  return 0xF800; // Red
        default:           return 0x0000; // Black (hidden)
    }
}

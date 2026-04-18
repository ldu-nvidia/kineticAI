/**
 * KineticAI Boot IMU Firmware — M5StickC Plus2
 *
 * Dual-mode operation:
 *   1. BLE: Streams 6-axis IMU at 50 Hz to phone for real-time coaching
 *   2. Local: Records full 200 Hz IMU data to flash memory
 *   3. WiFi: After skiing, serves recorded data over HTTP for bulk transfer
 *
 * Flash this to BOTH M5StickC Plus2 units.
 * Change BOOT_SIDE below to 'L' or 'R' before flashing each one.
 *
 * Buttons:
 *   A (front)  — Toggle WiFi transfer mode (when not skiing)
 *   B (side)   — Mark a moment (timestamp sent to phone + stored locally)
 *   C (power)  — Power on/off
 *
 * BLE Protocol:
 *   Service:  4d430001-0000-1000-8000-00805f9b34fb
 *   IMU data: 4d430002-... → 24 bytes [6 × float32] at 50 Hz (notify)
 *   Side:     4d430003-... → 1 byte 'L'/'R' (read)
 *   Command:  4d430004-... → 1 byte command from phone (write)
 *
 * WiFi Transfer:
 *   Starts HTTP server on port 80 when activated via Button A.
 *   GET /data   → returns all recorded IMU data as binary
 *   GET /status → returns JSON with record count, boot side, battery
 *   GET /clear  → clears recorded data
 */

#include <M5Unified.h>
#include <Wire.h>
// NimBLE is ~50% smaller than Arduino's built-in Bluedroid BLE stack
// (required to fit in the M5StickC Plus2's IRAM budget).
#include <NimBLEDevice.h>
#include <Preferences.h>

// ════════════════════════════════════════
//  WiFi bulk-transfer mode — OPTIONAL
// ════════════════════════════════════════
// Set to 1 to enable post-run WiFi dump of 200 Hz recorded data over HTTP.
// Leave at 0 for a BLE-only build (fits M5StickC Plus2's ~128KB IRAM).
// WiFi+BT coexistence places ~40KB of extra code into IRAM; the default
// BLE-only build is leaner and recommended while iterating on firmware.
#define ENABLE_WIFI_TRANSFER 0

#if ENABLE_WIFI_TRANSFER
  #include <WiFi.h>
  #include <WebServer.h>
#endif

// ════════════════════════════════════════
//  External sensors — OPTIONAL
// ════════════════════════════════════════
// Set to 1 once you've wired LSM9DS1 / BNO055 / VL6180X / AMG8833 to Grove port.
// When 0, all external-sensor code is compiled out — a bare M5StickC Plus with
// just the built-in IMU + buttons + LCD + buzzer builds and runs cleanly.
#define ENABLE_EXTERNAL_SENSORS 0

// ════════════════════════════════════════
//  CHANGE THIS FOR EACH BOOT: 'L' or 'R'
// ════════════════════════════════════════
#define BOOT_SIDE 'L'
// ════════════════════════════════════════

// WiFi credentials — set to your phone hotspot or home WiFi
#define WIFI_SSID "KineticAI-Hotspot"
#define WIFI_PASS "kineticai2026"

// BLE UUIDs
#define SERVICE_UUID   "4d430001-0000-1000-8000-00805f9b34fb"
#define IMU_CHAR_UUID  "4d430002-0000-1000-8000-00805f9b34fb"
#define SIDE_CHAR_UUID "4d430003-0000-1000-8000-00805f9b34fb"
#define CMD_CHAR_UUID  "4d430004-0000-1000-8000-00805f9b34fb"
#define MIC_CHAR_UUID  "4d430005-0000-1000-8000-00805f9b34fb"
#define PROX_CHAR_UUID "4d430006-0000-1000-8000-00805f9b34fb"
#define DUAL_CHAR_UUID "4d430007-0000-1000-8000-00805f9b34fb"
#define TRI_CHAR_UUID  "4d430008-0000-1000-8000-00805f9b34fb"
#define THERM_CHAR_UUID "4d430009-0000-1000-8000-00805f9b34fb"

// Local includes — order matters: each header's dependencies must come first
#include "power_manager.h"      // defines PowerConfig, PowerState, currentConfig
#include "proximity.h"          // defines ProximityResult, PROX_WARNING, etc.
#include "mic_analysis.h"       // defines MicAnalysisResult, uses PowerConfig
#include "display.h"            // uses all of the above + BOOT_SIDE
#include "custom_display.h"
#if ENABLE_EXTERNAL_SENSORS
  #include "external_sensors.h"
  #include "dual_imu.h"
  #include "tri_segment.h"
  #include "thermal_vision.h"
#else
  // Stub globals so the rest of the sketch compiles without external-sensor code
  struct { bool valid = false; float accelX, accelY, accelZ, gyroX, gyroY, gyroZ, magX, magY, magZ; } extImu;
  struct { bool valid = false; uint8_t calSys = 0; float roll = 0, pitch = 0, heading = 0, linAccelX = 0, linAccelY = 0, linAccelZ = 0; } bnoData;
  struct { bool airborne = false; uint16_t rangeMm = 0; } distData;
  struct { bool valid = false; bool frostbiteWarning = false; bool fusedAlert = false; } thermalAnalysis;
  struct { bool valid = false; } thermalData;
  struct { bool valid = false; float shellRoll = 0, shellPitch = 0, cuffRoll = 0, cuffPitch = 0; } dualMetrics;
  struct { bool valid = false; bool aclWarning = false; } triMetrics;
  static bool lsm9ds1Present = false;
  static bool bno055Present = false;
  static inline void initExternalSensors() {}
  static inline void readLSM9DS1() {}
  static inline void readBNO055() {}
  static inline void readVL6180X() {}
  static inline void readAMG8833() {}
  static inline void updateThermalAnalysis() {}
  static inline void packThermalAnalysis(uint8_t* out) { memset(out, 0, 12); }
  static inline void updateDualImu(float, float, float, float, float, float,
                                   float, float, float, float, float, float,
                                   float, float, float, uint16_t, bool, float) {}
  static inline void packDualImuMetrics(uint8_t* out) { memset(out, 0, 10); }
  static inline void updateTriSegment(float, float, float, float, float, float, float,
                                      float, float, float, float, uint8_t,
                                      bool, bool, float) {}
  static inline void packTriSegmentMetrics(uint8_t* out) { memset(out, 0, 12); }
#endif

// Vibration motor pin (connected to Grove port GPIO25 or GPIO26)
#define VIBRO_PIN      25  // -1 if not connected

// Timing
#define BLE_INTERVAL_MS    20   // 50 Hz BLE streaming
#define RECORD_INTERVAL_MS  5   // 200 Hz local recording
#define BUZZER_PIN          2

// ── Recording buffer ──
// Each record: 4 bytes timestamp + 24 bytes IMU = 28 bytes
// 8MB flash via Preferences can hold ~280,000 records (~23 min at 200 Hz)
// We use PSRAM-backed RAM buffer, flush to Preferences in chunks
struct ImuRecord {
    uint32_t timestampMs;
    float accelX, accelY, accelZ;
    float gyroX, gyroY, gyroZ;
};

// Circular buffer in PSRAM (2MB available, use 1.5MB = ~53,571 records = ~4.5 min)
// For longer runs, we keep the most recent data
#define MAX_RECORDS 53000
ImuRecord* recordBuffer = nullptr;
volatile uint32_t recordHead = 0;
volatile uint32_t recordCount = 0;
volatile bool recording = false;

// BLE state
NimBLEServer* pServer = nullptr;
NimBLECharacteristic* pImuChar = nullptr;
NimBLECharacteristic* pSideChar = nullptr;
NimBLECharacteristic* pCmdChar = nullptr;
NimBLECharacteristic* pMicChar = nullptr;
NimBLECharacteristic* pProxChar = nullptr;
NimBLECharacteristic* pDualChar = nullptr;
NimBLECharacteristic* pTriChar = nullptr;
NimBLECharacteristic* pThermChar = nullptr;
bool bleConnected = false;
bool wasConnected = false;

// WiFi state
bool wifiMode = false;
#if ENABLE_WIFI_TRANSFER
WebServer* webServer = nullptr;
#endif

// Timing
unsigned long lastBleTime = 0;
unsigned long lastRecordTime = 0;
unsigned long lastDisplayTime = 0;
uint32_t startTimeMs = 0;

// Turn detection (simple, for buzzer feedback)
float lastGyroZ = 0;
unsigned long turnCount = 0;
unsigned long lastBuzzTime = 0;

// Marker events
#define MAX_MARKERS 100
uint32_t markers[MAX_MARKERS];
uint8_t markerCount = 0;

const char* deviceName = (BOOT_SIDE == 'L') ? "KineticAI-L" : "KineticAI-R";

// Forward declarations (definitions appear later in this file)
void clearRecords();

// ── BLE Callbacks ──

class ServerCallbacks : public NimBLEServerCallbacks {
    void onConnect(NimBLEServer* s, NimBLEConnInfo& info) override { bleConnected = true; }
    void onDisconnect(NimBLEServer* s, NimBLEConnInfo& info, int reason) override { bleConnected = false; }
};

class CmdCallback : public NimBLECharacteristicCallbacks {
    void onWrite(NimBLECharacteristic* c, NimBLEConnInfo& info) override {
        // NimBLE returns std::string from getValue()
        std::string val = c->getValue();
        if (val.length() > 0) {
            uint8_t cmd = (uint8_t)val[0];
            switch (cmd) {
                case 0x01: recording = true; break;
                case 0x02: recording = false; break;
                case 0x03: clearRecords(); break;
                default:
                    if (cmd >= 0x30 && cmd <= 0x33) {
                        processPowerCommand((const uint8_t*)val.data(), val.length());
                    } else if (cmd >= 0x10) {
                        processCustomCommand((const uint8_t*)val.data(), val.length());
                    }
                    break;
            }
        }
    }
};

void clearRecords() {
    recordHead = 0;
    recordCount = 0;
    markerCount = 0;
}

// ── Setup ──

// ── Battery voltage (direct AXP192 I2C read) ──
// Workaround: M5.Power uses legacy ADC API which conflicts with Arduino
// ESP32 core 3.x driver_ng. We read the AXP192 PMIC directly over internal I2C.
// AXP192 is at 7-bit address 0x34 on M5StickC Plus; battery voltage is a
// 12-bit value at registers 0x78–0x79, scaled at 1.1 mV per LSB.
float readBatteryVoltageAxp() {
    uint8_t buf[2] = {0, 0};
    if (!M5.In_I2C.readRegister(0x34, 0x78, buf, 2, 400000)) return 0.0f;
    uint16_t raw = ((uint16_t)buf[0] << 4) | (buf[1] & 0x0F);
    return raw * 0.0011f; // volts
}

void setup() {
    Serial.begin(115200);
    delay(200);
    Serial.println("\n\n===== KineticAI boot =====");

    auto cfg = M5.config();
    cfg.output_power = false;
    cfg.internal_mic = false;
    cfg.internal_spk = false;
    Serial.println("[1] M5.begin() ...");
    M5.begin(cfg);
    Serial.println("[1] M5.begin() OK");

    M5.Display.setRotation(1);
    M5.Display.fillScreen(BLACK);
    Serial.println("[2] Display OK");

    recordBuffer = (ImuRecord*)malloc(10000 * sizeof(ImuRecord));
    Serial.printf("[3] Record buffer malloc %s\n", recordBuffer ? "OK" : "FAILED");

    drawStatus("Starting...", TFT_YELLOW);
    Serial.println("[4] drawStatus OK");

    Serial.println("[5] NimBLEDevice::init ...");
    NimBLEDevice::init(deviceName);
    Serial.println("[5] NimBLEDevice::init OK");
    pServer = NimBLEDevice::createServer();
    pServer->setCallbacks(new ServerCallbacks());

    NimBLEService* pService = pServer->createService(SERVICE_UUID);

    pImuChar = pService->createCharacteristic(
        IMU_CHAR_UUID,
        NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY
    );

    pSideChar = pService->createCharacteristic(
        SIDE_CHAR_UUID,
        NIMBLE_PROPERTY::READ
    );
    uint8_t sideVal = BOOT_SIDE;
    pSideChar->setValue(&sideVal, 1);

    pCmdChar = pService->createCharacteristic(
        CMD_CHAR_UUID,
        NIMBLE_PROPERTY::WRITE
    );
    pCmdChar->setCallbacks(new CmdCallback());

    pMicChar = pService->createCharacteristic(
        MIC_CHAR_UUID,
        NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY
    );

    pProxChar = pService->createCharacteristic(
        PROX_CHAR_UUID,
        NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY
    );

    pDualChar = pService->createCharacteristic(
        DUAL_CHAR_UUID,
        NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY
    );

    pTriChar = pService->createCharacteristic(
        TRI_CHAR_UUID,
        NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY
    );

    pThermChar = pService->createCharacteristic(
        THERM_CHAR_UUID,
        NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY
    );

    pService->start();
    Serial.println("[6] Service start OK");

    // Advertising: name in primary packet, 128-bit service UUID in scan response.
    // Putting both in the 31-byte primary packet would silently truncate the name
    // and make the device show up as anonymous in BLE scanners.
    NimBLEAdvertising* pAdvertising = NimBLEDevice::getAdvertising();

    NimBLEAdvertisementData advData;
    advData.setFlags(BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP);
    advData.setName(deviceName);
    pAdvertising->setAdvertisementData(advData);

    NimBLEAdvertisementData scanRespData;
    scanRespData.setCompleteServices(NimBLEUUID(SERVICE_UUID));
    pAdvertising->setScanResponseData(scanRespData);

    pAdvertising->setPreferredParams(0x06, 0x12);
    NimBLEDevice::startAdvertising();
    Serial.println("[7] Advertising started");

    startTimeMs = millis();
    recording = true;

    pinMode(BUZZER_PIN, OUTPUT);
    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, LOW);
    Serial.println("[8] GPIO init OK");

    customDisplayInit();
    Serial.println("[9] customDisplayInit OK");
    micInit();
    Serial.println("[10] micInit OK");
    proximityInit();
    Serial.println("[11] proximityInit OK");
    initExternalSensors();
    Serial.println("[12] initExternalSensors OK");

    if (VIBRO_PIN >= 0) {
        pinMode(VIBRO_PIN, OUTPUT);
        analogWrite(VIBRO_PIN, 0);
    }
    Serial.println("[13] Vibro pin OK");

    drawStatus("Ready", TFT_GREEN);
    Serial.println("===== setup() complete =====\n");
}

// ── Main Loop ──

void loop() {
    static unsigned long lastHeartbeat = 0;
    if (millis() - lastHeartbeat > 2000) {
        lastHeartbeat = millis();
        Serial.printf("[loop] alive t=%lu ms, bleConn=%d\n", millis(), bleConnected);
    }

    M5.update();

    // Button A: short press = WiFi mode, long hold (>2s) = party mode
    if (M5.BtnA.wasReleaseFor(2000)) {
        togglePartyMode();
        if (partyMode) {
            tone(BUZZER_PIN, 1000, 50);
            delay(80);
            tone(BUZZER_PIN, 1500, 50);
            delay(80);
            tone(BUZZER_PIN, 2000, 50);
        }
    }
#if ENABLE_WIFI_TRANSFER
    else if (M5.BtnA.wasPressed()) {
        if (!wifiMode) {
            startWifiMode();
        } else {
            stopWifiMode();
        }
    }
#endif

    // Button B: mark a moment
    if (M5.BtnB.wasPressed()) {
        if (markerCount < MAX_MARKERS) {
            markers[markerCount++] = millis() - startTimeMs;
            // Confirmation buzz
            tone(BUZZER_PIN, 1500, 50);
        }
    }

#if ENABLE_WIFI_TRANSFER
    if (wifiMode) {
        webServer->handleClient();
        return;
    }
#endif

    // Handle BLE reconnection
    if (!bleConnected && wasConnected) {
        delay(200);
        NimBLEDevice::startAdvertising();
        wasConnected = false;
    }
    if (bleConnected && !wasConnected) {
        wasConnected = true;
        // Connection buzz: two ascending tones
        tone(BUZZER_PIN, 800, 80);
        delay(100);
        tone(BUZZER_PIN, 1200, 80);
    }

    unsigned long now = millis();

    // Read IMU — must explicitly poll on this M5Unified+core combo;
    // M5.update() only polls buttons, not the IMU
    M5.Imu.update();
    auto imuData = M5.Imu.getImuData();

    float packet[6];
    packet[0] = imuData.accel.x * 9.81f;
    packet[1] = imuData.accel.y * 9.81f;
    packet[2] = imuData.accel.z * 9.81f;
    packet[3] = imuData.gyro.x * 0.01745329f;
    packet[4] = imuData.gyro.y * 0.01745329f;
    packet[5] = imuData.gyro.z * 0.01745329f;

    // Diagnostic: print IMU every second to confirm it's actually reading new data
    static unsigned long lastImuPrint = 0;
    if (millis() - lastImuPrint > 1000) {
        lastImuPrint = millis();
        Serial.printf("[imu] accel=(%.2f,%.2f,%.2f) gyro=(%.2f,%.2f,%.2f)\n",
            imuData.accel.x, imuData.accel.y, imuData.accel.z,
            imuData.gyro.x, imuData.gyro.y, imuData.gyro.z);
    }

    // ── Local Recording (rate from power config) ──
    bool shouldRecord = currentConfig.recordingEnabled && recording && recordBuffer;
    if (shouldRecord && currentConfig.recordIntervalMs > 0 && (now - lastRecordTime >= currentConfig.recordIntervalMs)) {
        lastRecordTime = now;
        uint32_t idx = recordHead % MAX_RECORDS;
        recordBuffer[idx].timestampMs = now - startTimeMs;
        recordBuffer[idx].accelX = packet[0];
        recordBuffer[idx].accelY = packet[1];
        recordBuffer[idx].accelZ = packet[2];
        recordBuffer[idx].gyroX = packet[3];
        recordBuffer[idx].gyroY = packet[4];
        recordBuffer[idx].gyroZ = packet[5];
        recordHead++;
        if (recordCount < MAX_RECORDS) recordCount++;
    }

    // ── External Sensors: LSM9DS1 + BNO055 + VL6180X ──
    readLSM9DS1();
    readBNO055();
    static unsigned long lastDistRead = 0;
    if (now - lastDistRead >= 100) { // 10 Hz for both slow sensors
        lastDistRead = now;
        readVL6180X();
        readAMG8833();
        if (thermalData.valid) {
            updateThermalAnalysis();

            // Send thermal data over BLE
            if (bleConnected && pThermChar) {
                uint8_t thermPacket[12];
                packThermalAnalysis(thermPacket);
                pThermChar->setValue(thermPacket, 12);
                pThermChar->notify();
            }

            // Frostbite warning
            if (thermalAnalysis.frostbiteWarning) {
                static unsigned long lastFrostBuzz = 0;
                if (now - lastFrostBuzz > 30000) { // Every 30 seconds
                    lastFrostBuzz = now;
                    tone(BUZZER_PIN, 500, 200);
                    delay(250);
                    tone(BUZZER_PIN, 500, 200);
                }
            }

            // Fused person detection alert (thermal + radar agree)
            if (thermalAnalysis.fusedAlert && proxResult.level >= PROX_WARNING) {
                // Both thermal and radar confirm person approaching — highest alert
                if (VIBRO_PIN >= 0) analogWrite(VIBRO_PIN, 255);
                tone(BUZZER_PIN, 2500, 150);
            }
        }
    }

    float dt = currentConfig.bleIntervalMs / 1000.0f;

    // ── Derived motion quantities (used by tri-segment, turn detection, displays) ──
    float edgeAngle = fabsf(atan2f(packet[0],
        sqrtf(packet[1]*packet[1] + packet[2]*packet[2]))) * 57.296f;
    float gForce = sqrtf(packet[0]*packet[0] + packet[1]*packet[1] + packet[2]*packet[2]) / 9.81f;
    float pitch = atan2f(packet[1], sqrtf(packet[0]*packet[0] + packet[2]*packet[2])) * 57.296f;

    // ── Dual IMU Analysis (shell + cuff) ──
    if (extImu.valid) {
        updateDualImu(
            packet[0], packet[1], packet[2], packet[3], packet[4], packet[5],
            extImu.accelX, extImu.accelY, extImu.accelZ,
            extImu.gyroX, extImu.gyroY, extImu.gyroZ,
            extImu.magX, extImu.magY, extImu.magZ,
            distData.rangeMm, distData.airborne,
            dt
        );
    }

    // ── Tri-Segment Analysis (shell + cuff + shin) ──
    if (extImu.valid && bnoData.valid) {
        float shellGyroMag = sqrtf(packet[3]*packet[3]+packet[4]*packet[4]+packet[5]*packet[5]);
        float cuffGyroMag = sqrtf(extImu.gyroX*extImu.gyroX+extImu.gyroY*extImu.gyroY+extImu.gyroZ*extImu.gyroZ);
        float shinAccelMag = sqrtf(bnoData.linAccelX*bnoData.linAccelX+bnoData.linAccelY*bnoData.linAccelY+bnoData.linAccelZ*bnoData.linAccelZ);

        updateTriSegment(
            dualMetrics.shellRoll, dualMetrics.shellPitch,
            dualMetrics.cuffRoll, dualMetrics.cuffPitch,
            bnoData.roll, bnoData.pitch, bnoData.heading,
            shellGyroMag, cuffGyroMag, shinAccelMag,
            gForce, bnoData.calSys,
            lsm9ds1Present, bno055Present,
            dt
        );
    }

    // ── BLE Streaming: Merged Motion Packet (34 bytes @ 50Hz) ──
    // Combines IMU + DualIMU + TriSegment into one notification
    if (bleConnected && (now - lastBleTime >= currentConfig.bleIntervalMs)) {
        lastBleTime = now;

        static uint32_t notifyCount = 0;
        notifyCount++;
        if (notifyCount % 50 == 0) {
            Serial.printf("[ble] notify #%u sent\n", notifyCount);
        }

        uint8_t motionPacket[34];
        // Bytes 0-11: Compressed IMU (6x int16)
        packImuCompressed(packet, motionPacket);
        // Bytes 12-21: Dual IMU metrics
        if (dualMetrics.valid) {
            packDualImuMetrics(motionPacket + 12);
        } else {
            memset(motionPacket + 12, 0, 10);
        }
        // Bytes 22-33: Tri-segment metrics
        if (triMetrics.valid) {
            packTriSegmentMetrics(motionPacket + 22);
        } else {
            memset(motionPacket + 22, 0, 12);
        }

        pImuChar->setValue(motionPacket, 34);
        pImuChar->notify();
    }

    // ACL warning: immediate alert via buzzer + vibration
    if (triMetrics.valid && triMetrics.aclWarning) {
        static unsigned long lastAclBuzz = 0;
        if (now - lastAclBuzz > 1000) {
            lastAclBuzz = now;
            tone(BUZZER_PIN, 800, 300);
            delay(50);
            tone(BUZZER_PIN, 600, 300);
            if (VIBRO_PIN >= 0) analogWrite(VIBRO_PIN, 255);
            ledPattern = 3;
            ledPatternStart = now;
        }
    }

    // ── Turn Detection + Rich Feedback ──
    // (edgeAngle, gForce, pitch already computed above)
    bool turnDetected = false;
    if (lastGyroZ > 0.5f && packet[5] <= 0.5f) turnDetected = true;
    if (lastGyroZ < -0.5f && packet[5] >= -0.5f) turnDetected = true;

    if (turnDetected) {
        turnCount++;
        onTurnCompleted(edgeAngle, gForce, pitch);
        buzzTurnFeedback(packet);
        checkBrakingSignal(packet[5], lastGyroZ);
    }

    lastGyroZ = packet[5];

    // Balance warning check (every BLE frame)
    checkBalanceWarning(pitch);

    // ── Microphone Analysis (rate from power config) ──
    if (currentConfig.micEnabled && runMicAnalysis()) {
        // Merged Environment Packet (18 bytes @ 2Hz): Mic + Thermal
        if (bleConnected && pMicChar) {
            uint8_t envPacket[18];
            // Bytes 0-5: Mic analysis
            packMicResult(envPacket);
            // Bytes 6-17: Thermal analysis
            if (thermalData.valid) {
                packThermalAnalysis(envPacket + 6);
            } else {
                memset(envPacket + 6, 0, 12);
            }
            pMicChar->setValue(envPacket, 18);
            pMicChar->notify();
        }

        // Fall detection: trigger LED warning + buzzer
        if (micResult.fallDetected) {
            ledPattern = 3;
            ledPatternStart = millis();
            tone(BUZZER_PIN, 400, 500);
        }

        // Binding click: confirmation beep
        if (micResult.bindingClick) {
            tone(BUZZER_PIN, 2000, 100);
        }
    }

    // ── Proximity Radar (10 Hz) ──
    if (readProximity()) {
        // Send proximity data over BLE
        if (bleConnected && pProxChar) {
            uint8_t proxPacket[4];
            packProximityResult(proxPacket);
            pProxChar->setValue(proxPacket, 4);
            pProxChar->notify();
        }

        // Execute warnings (LED + buzzer + vibration)
        if (proxResult.level > PROX_CLEAR) {
            executeProximityWarning(BUZZER_PIN, LED_PIN, VIBRO_PIN);
        }
    }

    // ── LED Update (every loop iteration for smooth patterns) ──
    updateLed();

    // ── Display Update (rate from power config) ──
    if (now - lastDisplayTime >= currentConfig.displayIntervalMs) {
        lastDisplayTime = now;
        drawCustomDisplay(packet, bleConnected, recordCount);
    }

    // Auto-sleep check (60s no motion → deep sleep)
    float gyroMag = sqrtf(packet[3]*packet[3] + packet[4]*packet[4] + packet[5]*packet[5]);
    checkAutoSleep(gyroMag);

    // Short sleep to save power between samples
    if (currentPowerState == PWR_LIFT) {
        delay(50); // More aggressive sleep on lift
    } else if (!bleConnected && !recording) {
        delay(10);
    }
}

// ── Buzzer Feedback ──

void buzzTurnFeedback(float* packet) {
    unsigned long now = millis();
    if (now - lastBuzzTime < 500) return;
    lastBuzzTime = now;

    float edgeAngle = fabsf(atan2f(packet[0],
        sqrtf(packet[1]*packet[1] + packet[2]*packet[2]))) * 57.296f;

    // Higher edge angle → higher pitch (good turn)
    int freq;
    int dur;
    if (edgeAngle > 30) {
        freq = 1800;  // excellent
        dur = 40;
    } else if (edgeAngle > 20) {
        freq = 1400;  // good
        dur = 40;
    } else if (edgeAngle > 10) {
        freq = 1000;  // moderate
        dur = 30;
    } else {
        freq = 600;   // low edge
        dur = 60;
    }

    tone(BUZZER_PIN, freq, dur);
}

// ── WiFi Transfer Mode ──
#if ENABLE_WIFI_TRANSFER

void startWifiMode() {
    wifiMode = true;
    recording = false;

    // Stop BLE to free radio for WiFi
    NimBLEDevice::deinit(true);

    M5.Display.fillScreen(BLACK);
    M5.Display.setTextSize(2);
    M5.Display.setTextColor(TFT_YELLOW);
    M5.Display.setCursor(10, 10);
    M5.Display.printf("WiFi Mode");
    M5.Display.setTextSize(1);
    M5.Display.setTextColor(TFT_WHITE);
    M5.Display.setCursor(10, 40);
    M5.Display.printf("Connecting to:");
    M5.Display.setCursor(10, 55);
    M5.Display.printf("%s", WIFI_SSID);

    WiFi.begin(WIFI_SSID, WIFI_PASS);

    int attempts = 0;
    while (WiFi.status() != WL_CONNECTED && attempts < 30) {
        delay(500);
        attempts++;
        M5.Display.setCursor(10, 75);
        M5.Display.printf("Attempt %d/30  ", attempts);
    }

    if (WiFi.status() != WL_CONNECTED) {
        M5.Display.setCursor(10, 95);
        M5.Display.setTextColor(TFT_RED);
        M5.Display.printf("WiFi failed!");
        M5.Display.setCursor(10, 115);
        M5.Display.printf("Press A to retry");
        WiFi.disconnect();
        wifiMode = false;
        // Restart BLE
        NimBLEDevice::init(deviceName);
        return;
    }

    IPAddress ip = WiFi.localIP();

    webServer = new WebServer(80);

    webServer->on("/status", HTTP_GET, []() {
        float batV = readBatteryVoltageAxp();
        String json = "{";
        json += "\"side\":\"" + String((char)BOOT_SIDE) + "\",";
        json += "\"records\":" + String(recordCount) + ",";
        json += "\"maxRecords\":" + String(MAX_RECORDS) + ",";
        json += "\"markers\":" + String(markerCount) + ",";
        json += "\"batteryV\":" + String(batV, 2) + ",";
        json += "\"uptimeMs\":" + String(millis()) + "}";
        webServer->send(200, "application/json", json);
    });

    webServer->on("/data", HTTP_GET, []() {
        uint32_t count = recordCount;
        uint32_t startIdx = (recordHead >= count) ? (recordHead - count) : 0;

        // Send as binary: header (8 bytes) + records (28 bytes each)
        size_t totalSize = 8 + count * sizeof(ImuRecord);

        webServer->setContentLength(totalSize);
        webServer->send(200, "application/octet-stream", "");

        // Header: side (1 byte) + padding (3 bytes) + count (4 bytes)
        uint8_t header[8];
        header[0] = BOOT_SIDE;
        header[1] = 0; header[2] = 0; header[3] = 0;
        memcpy(header + 4, &count, 4);
        webServer->sendContent(String((char*)header, 8));

        // Send records in chunks
        const int CHUNK = 100;
        for (uint32_t i = 0; i < count; i += CHUNK) {
            uint32_t batchSize = min((uint32_t)CHUNK, count - i);
            uint32_t idx = (startIdx + i) % MAX_RECORDS;

            for (uint32_t j = 0; j < batchSize; j++) {
                uint32_t ri = (idx + j) % MAX_RECORDS;
                webServer->sendContent(String((char*)&recordBuffer[ri], sizeof(ImuRecord)));
            }
        }
    });

    webServer->on("/markers", HTTP_GET, []() {
        String json = "[";
        for (uint8_t i = 0; i < markerCount; i++) {
            if (i > 0) json += ",";
            json += String(markers[i]);
        }
        json += "]";
        webServer->send(200, "application/json", json);
    });

    webServer->on("/clear", HTTP_GET, []() {
        clearRecords();
        webServer->send(200, "text/plain", "OK");
    });

    webServer->begin();

    M5.Display.fillScreen(BLACK);
    M5.Display.setTextSize(2);
    M5.Display.setTextColor(TFT_GREEN);
    M5.Display.setCursor(10, 10);
    M5.Display.printf("WiFi Ready");
    M5.Display.setTextSize(1);
    M5.Display.setTextColor(TFT_WHITE);
    M5.Display.setCursor(10, 40);
    M5.Display.printf("IP: %s", ip.toString().c_str());
    M5.Display.setCursor(10, 60);
    M5.Display.printf("Records: %lu", recordCount);
    M5.Display.setCursor(10, 80);
    M5.Display.printf("Markers: %d", markerCount);
    M5.Display.setCursor(10, 105);
    M5.Display.setTextColor(TFT_CYAN);
    M5.Display.printf("Phone: open app and");
    M5.Display.setCursor(10, 118);
    M5.Display.printf("tap 'Download Data'");
}

void stopWifiMode() {
    if (webServer) {
        webServer->stop();
        delete webServer;
        webServer = nullptr;
    }
    WiFi.disconnect();
    wifiMode = false;

    // Restart BLE (NimBLE 2.x)
    NimBLEDevice::init(deviceName);
    pServer = NimBLEDevice::createServer();
    pServer->setCallbacks(new ServerCallbacks());
    NimBLEService* pService = pServer->createService(SERVICE_UUID);

    pImuChar = pService->createCharacteristic(
        IMU_CHAR_UUID,
        NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY
    );

    pSideChar = pService->createCharacteristic(
        SIDE_CHAR_UUID,
        NIMBLE_PROPERTY::READ
    );
    uint8_t sideVal = BOOT_SIDE;
    pSideChar->setValue(&sideVal, 1);

    pCmdChar = pService->createCharacteristic(
        CMD_CHAR_UUID,
        NIMBLE_PROPERTY::WRITE
    );
    pCmdChar->setCallbacks(new CmdCallback());

    pService->start();
    NimBLEDevice::startAdvertising();

    recording = true;
    drawStatus("BLE Mode", TFT_GREEN);
}

#endif // ENABLE_WIFI_TRANSFER

// ── Display ──

void drawStatus(const char* status, uint16_t color) {
    M5.Display.fillScreen(BLACK);
    M5.Display.setTextColor(color);
    M5.Display.setCursor(10, 10);
    M5.Display.setTextSize(3);
    M5.Display.printf("KineticAI");
    M5.Display.setCursor(10, 45);
    M5.Display.setTextSize(4);
    M5.Display.printf("%c", BOOT_SIDE);
    M5.Display.setCursor(10, 90);
    M5.Display.setTextSize(2);
    M5.Display.setTextColor(TFT_WHITE);
    M5.Display.printf("%s", status);

    float batV = readBatteryVoltageAxp();
    int batPct = constrain((int)((batV - 3.2f) / 0.9f * 100), 0, 100);
    M5.Display.setCursor(10, 118);
    M5.Display.setTextSize(1);
    M5.Display.setTextColor(batPct > 20 ? TFT_GREEN : TFT_RED);
    M5.Display.printf("BAT:%d%% %.2fV  REC:%lu", batPct, batV, recordCount);
}

// Display functions are now in display.h

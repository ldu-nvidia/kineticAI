/**
 * MyCarv Boot IMU Firmware — M5StickC Plus2
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

#include <M5StickCPlus2.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <WiFi.h>
#include <WebServer.h>
#include <Preferences.h>
#include "display.h"
#include "custom_display.h"
#include "mic_analysis.h"
#include "power_manager.h"
#include "proximity.h"
#include "external_sensors.h"
#include "dual_imu.h"
#include "tri_segment.h"
#include "thermal_vision.h"

// ════════════════════════════════════════
//  CHANGE THIS FOR EACH BOOT: 'L' or 'R'
// ════════════════════════════════════════
#define BOOT_SIDE 'L'
// ════════════════════════════════════════

// WiFi credentials — set to your phone hotspot or home WiFi
#define WIFI_SSID "MyCarv-Hotspot"
#define WIFI_PASS "mycarv2026"

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
BLEServer* pServer = nullptr;
BLECharacteristic* pImuChar = nullptr;
BLECharacteristic* pSideChar = nullptr;
BLECharacteristic* pCmdChar = nullptr;
BLECharacteristic* pMicChar = nullptr;
BLECharacteristic* pProxChar = nullptr;
BLECharacteristic* pDualChar = nullptr;
BLECharacteristic* pTriChar = nullptr;
BLECharacteristic* pThermChar = nullptr;
bool bleConnected = false;
bool wasConnected = false;

// WiFi state
bool wifiMode = false;
WebServer* webServer = nullptr;

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

const char* deviceName = (BOOT_SIDE == 'L') ? "MyCarv-L" : "MyCarv-R";

// ── BLE Callbacks ──

class ServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer* s) override { bleConnected = true; }
    void onDisconnect(BLEServer* s) override { bleConnected = false; }
};

class CmdCallback : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* c) override {
        std::string val = c->getValue();
        if (val.length() > 0) {
            uint8_t cmd = val[0];
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

void setup() {
    auto cfg = M5.config();
    StickCP2.begin(cfg);

    StickCP2.Display.setRotation(1);
    StickCP2.Display.fillScreen(BLACK);

    // Allocate record buffer in PSRAM
    recordBuffer = (ImuRecord*)ps_malloc(MAX_RECORDS * sizeof(ImuRecord));
    if (!recordBuffer) {
        recordBuffer = (ImuRecord*)malloc(10000 * sizeof(ImuRecord));
    }

    drawStatus("Starting...", TFT_YELLOW);

    // Initialize BLE
    BLEDevice::init(deviceName);
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new ServerCallbacks());

    BLEService* pService = pServer->createService(SERVICE_UUID);

    pImuChar = pService->createCharacteristic(
        IMU_CHAR_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
    );
    pImuChar->addDescriptor(new BLE2902());

    pSideChar = pService->createCharacteristic(
        SIDE_CHAR_UUID,
        BLECharacteristic::PROPERTY_READ
    );
    uint8_t sideVal = BOOT_SIDE;
    pSideChar->setValue(&sideVal, 1);

    pCmdChar = pService->createCharacteristic(
        CMD_CHAR_UUID,
        BLECharacteristic::PROPERTY_WRITE
    );
    pCmdChar->setCallbacks(new CmdCallback());

    pMicChar = pService->createCharacteristic(
        MIC_CHAR_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
    );
    pMicChar->addDescriptor(new BLE2902());

    pProxChar = pService->createCharacteristic(
        PROX_CHAR_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
    );
    pProxChar->addDescriptor(new BLE2902());

    pDualChar = pService->createCharacteristic(
        DUAL_CHAR_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
    );
    pDualChar->addDescriptor(new BLE2902());

    pTriChar = pService->createCharacteristic(
        TRI_CHAR_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
    );
    pTriChar->addDescriptor(new BLE2902());

    pThermChar = pService->createCharacteristic(
        THERM_CHAR_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
    );
    pThermChar->addDescriptor(new BLE2902());

    pService->start();

    BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(SERVICE_UUID);
    pAdvertising->setScanResponse(true);
    pAdvertising->setMinPreferred(0x06);
    BLEDevice::startAdvertising();

    startTimeMs = millis();
    recording = true;

    pinMode(BUZZER_PIN, OUTPUT);
    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, LOW);

    customDisplayInit();
    micInit();
    proximityInit();
    initExternalSensors();

    // Vibration motor (optional, -1 = not connected)
    if (VIBRO_PIN >= 0) {
        pinMode(VIBRO_PIN, OUTPUT);
        analogWrite(VIBRO_PIN, 0);
    }

    drawStatus("Ready", TFT_GREEN);
}

// ── Main Loop ──

void loop() {
    StickCP2.update();

    // Button A: short press = WiFi mode, long hold (>2s) = party mode
    if (StickCP2.BtnA.wasReleaseFor(2000)) {
        togglePartyMode();
        if (partyMode) {
            tone(BUZZER_PIN, 1000, 50);
            delay(80);
            tone(BUZZER_PIN, 1500, 50);
            delay(80);
            tone(BUZZER_PIN, 2000, 50);
        }
    } else if (StickCP2.BtnA.wasPressed()) {
        if (!wifiMode) {
            startWifiMode();
        } else {
            stopWifiMode();
        }
    }

    // Button B: mark a moment
    if (StickCP2.BtnB.wasPressed()) {
        if (markerCount < MAX_MARKERS) {
            markers[markerCount++] = millis() - startTimeMs;
            // Confirmation buzz
            tone(BUZZER_PIN, 1500, 50);
        }
    }

    if (wifiMode) {
        webServer->handleClient();
        return;
    }

    // Handle BLE reconnection
    if (!bleConnected && wasConnected) {
        delay(200);
        BLEDevice::startAdvertising();
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

    // Read IMU
    auto imuData = StickCP2.Imu.getImuData();

    float packet[6];
    packet[0] = imuData.accel.x * 9.81f;
    packet[1] = imuData.accel.y * 9.81f;
    packet[2] = imuData.accel.z * 9.81f;
    packet[3] = imuData.gyro.x * 0.01745329f;
    packet[4] = imuData.gyro.y * 0.01745329f;
    packet[5] = imuData.gyro.z * 0.01745329f;

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

    // ── BLE Streaming (rate from power config) ──
    if (bleConnected && (now - lastBleTime >= currentConfig.bleIntervalMs)) {
        lastBleTime = now;
        // Send compressed 12-byte int16 packet instead of 24-byte float
        uint8_t compressedPacket[12];
        packImuCompressed(packet, compressedPacket);
        pImuChar->setValue(compressedPacket, 12);
        pImuChar->notify();

        // Send dual IMU metrics if external sensors present
        if (dualMetrics.valid && pDualChar) {
            uint8_t dualPacket[10];
            packDualImuMetrics(dualPacket);
            pDualChar->setValue(dualPacket, 10);
            pDualChar->notify();
        }

        // Send tri-segment metrics if all three IMUs present
        if (triMetrics.valid && pTriChar) {
            uint8_t triPacket[12];
            packTriSegmentMetrics(triPacket);
            pTriChar->setValue(triPacket, 12);
            pTriChar->notify();
        }
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
    float edgeAngle = fabsf(atan2f(packet[0],
        sqrtf(packet[1]*packet[1] + packet[2]*packet[2]))) * 57.296f;
    float gForce = sqrtf(packet[0]*packet[0] + packet[1]*packet[1] + packet[2]*packet[2]) / 9.81f;
    float pitch = atan2f(packet[1], sqrtf(packet[0]*packet[0] + packet[2]*packet[2])) * 57.296f;

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
        // Send mic data over BLE
        if (bleConnected && pMicChar) {
            uint8_t micPacket[6];
            packMicResult(micPacket);
            pMicChar->setValue(micPacket, 6);
            pMicChar->notify();
        }

        // Fall detection: trigger LED warning + buzzer
        if (micResult.fallDetected) {
            ledPattern = 3; // warning pattern
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

void startWifiMode() {
    wifiMode = true;
    recording = false;

    // Stop BLE to free radio for WiFi
    BLEDevice::deinit(true);

    StickCP2.Display.fillScreen(BLACK);
    StickCP2.Display.setTextSize(2);
    StickCP2.Display.setTextColor(TFT_YELLOW);
    StickCP2.Display.setCursor(10, 10);
    StickCP2.Display.printf("WiFi Mode");
    StickCP2.Display.setTextSize(1);
    StickCP2.Display.setTextColor(TFT_WHITE);
    StickCP2.Display.setCursor(10, 40);
    StickCP2.Display.printf("Connecting to:");
    StickCP2.Display.setCursor(10, 55);
    StickCP2.Display.printf("%s", WIFI_SSID);

    WiFi.begin(WIFI_SSID, WIFI_PASS);

    int attempts = 0;
    while (WiFi.status() != WL_CONNECTED && attempts < 30) {
        delay(500);
        attempts++;
        StickCP2.Display.setCursor(10, 75);
        StickCP2.Display.printf("Attempt %d/30  ", attempts);
    }

    if (WiFi.status() != WL_CONNECTED) {
        StickCP2.Display.setCursor(10, 95);
        StickCP2.Display.setTextColor(TFT_RED);
        StickCP2.Display.printf("WiFi failed!");
        StickCP2.Display.setCursor(10, 115);
        StickCP2.Display.printf("Press A to retry");
        WiFi.disconnect();
        wifiMode = false;
        // Restart BLE
        BLEDevice::init(deviceName);
        return;
    }

    IPAddress ip = WiFi.localIP();

    webServer = new WebServer(80);

    webServer->on("/status", HTTP_GET, []() {
        float batV = StickCP2.Power.getBatteryVoltage() / 1000.0f;
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

    StickCP2.Display.fillScreen(BLACK);
    StickCP2.Display.setTextSize(2);
    StickCP2.Display.setTextColor(TFT_GREEN);
    StickCP2.Display.setCursor(10, 10);
    StickCP2.Display.printf("WiFi Ready");
    StickCP2.Display.setTextSize(1);
    StickCP2.Display.setTextColor(TFT_WHITE);
    StickCP2.Display.setCursor(10, 40);
    StickCP2.Display.printf("IP: %s", ip.toString().c_str());
    StickCP2.Display.setCursor(10, 60);
    StickCP2.Display.printf("Records: %lu", recordCount);
    StickCP2.Display.setCursor(10, 80);
    StickCP2.Display.printf("Markers: %d", markerCount);
    StickCP2.Display.setCursor(10, 105);
    StickCP2.Display.setTextColor(TFT_CYAN);
    StickCP2.Display.printf("Phone: open app and");
    StickCP2.Display.setCursor(10, 118);
    StickCP2.Display.printf("tap 'Download Data'");
}

void stopWifiMode() {
    if (webServer) {
        webServer->stop();
        delete webServer;
        webServer = nullptr;
    }
    WiFi.disconnect();
    wifiMode = false;

    // Restart BLE
    BLEDevice::init(deviceName);
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new ServerCallbacks());
    BLEService* pService = pServer->createService(SERVICE_UUID);

    pImuChar = pService->createCharacteristic(
        IMU_CHAR_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
    );
    pImuChar->addDescriptor(new BLE2902());

    pSideChar = pService->createCharacteristic(
        SIDE_CHAR_UUID,
        BLECharacteristic::PROPERTY_READ
    );
    uint8_t sideVal = BOOT_SIDE;
    pSideChar->setValue(&sideVal, 1);

    pCmdChar = pService->createCharacteristic(
        CMD_CHAR_UUID,
        BLECharacteristic::PROPERTY_WRITE
    );
    pCmdChar->setCallbacks(new CmdCallback());

    pService->start();
    BLEDevice::startAdvertising();

    recording = true;
    drawStatus("BLE Mode", TFT_GREEN);
}

// ── Display ──

void drawStatus(const char* status, uint16_t color) {
    StickCP2.Display.fillScreen(BLACK);
    StickCP2.Display.setTextColor(color);
    StickCP2.Display.setCursor(10, 10);
    StickCP2.Display.setTextSize(3);
    StickCP2.Display.printf("MyCarv");
    StickCP2.Display.setCursor(10, 45);
    StickCP2.Display.setTextSize(4);
    StickCP2.Display.printf("%c", BOOT_SIDE);
    StickCP2.Display.setCursor(10, 90);
    StickCP2.Display.setTextSize(2);
    StickCP2.Display.setTextColor(TFT_WHITE);
    StickCP2.Display.printf("%s", status);

    float batV = StickCP2.Power.getBatteryVoltage() / 1000.0f;
    int batPct = constrain((int)((batV - 3.2f) / 0.9f * 100), 0, 100);
    StickCP2.Display.setCursor(10, 118);
    StickCP2.Display.setTextSize(1);
    StickCP2.Display.setTextColor(batPct > 20 ? TFT_GREEN : TFT_RED);
    StickCP2.Display.printf("BAT:%d%% %.2fV  REC:%lu", batPct, batV, recordCount);
}

// Display functions are now in display.h

/**
 * External Sensor Drivers: LSM9DS1 (9-axis IMU) + VL6180X (ToF distance)
 *
 * Both connect via I2C on the M5StickC Plus2 Grove port (SDA=G21, SCL=G22).
 * Daisy-chained on the same bus, different addresses.
 *
 * LSM9DS1 addresses: 0x6B (accel/gyro), 0x1E (magnetometer)
 * VL6180X address:   0x29
 */

#pragma once
#include <Wire.h>
#include <math.h>

// ── LSM9DS1 Registers ──
#define LSM9DS1_AG_ADDR   0x6B
#define LSM9DS1_MAG_ADDR  0x1E

// Accel/Gyro registers
#define LSM_WHO_AM_I_AG   0x0F  // Should return 0x68
#define LSM_CTRL_REG1_G   0x10  // Gyro control
#define LSM_CTRL_REG6_XL  0x20  // Accel control
#define LSM_CTRL_REG8     0x22  // General control
#define LSM_OUT_X_L_G     0x18  // Gyro data start
#define LSM_OUT_X_L_XL    0x28  // Accel data start

// Magnetometer registers
#define LSM_WHO_AM_I_M    0x0F  // Should return 0x3D
#define LSM_CTRL_REG1_M   0x20
#define LSM_CTRL_REG2_M   0x21
#define LSM_CTRL_REG3_M   0x22
#define LSM_OUT_X_L_M     0x28  // Mag data start

// ── BNO055 Registers ──
#define BNO055_ADDR         0x28
#define BNO055_CHIP_ID      0x00  // Should return 0xA0
#define BNO055_OPR_MODE     0x3D
#define BNO055_SYS_TRIGGER  0x3F
#define BNO055_UNIT_SEL     0x3B
#define BNO055_QUAT_W_LSB   0x20  // Quaternion data (16 bytes)
#define BNO055_LIA_X_LSB    0x28  // Linear acceleration (6 bytes)
#define BNO055_GRV_X_LSB    0x2E  // Gravity vector (6 bytes)
#define BNO055_EUL_H_LSB    0x1A  // Euler angles (6 bytes)
#define BNO055_CALIB_STAT   0x35  // Calibration status

// BNO055 operation modes
#define BNO055_MODE_CONFIG  0x00
#define BNO055_MODE_NDOF    0x0C  // 9-axis absolute orientation fusion

// ── VL6180X Registers ──
#define VL6180X_ADDR              0x29
#define VL6180X_ID                0x000
#define VL6180X_SYSTEM_FRESH_OUT  0x016
#define VL6180X_SYSRANGE_START    0x018
#define VL6180X_RESULT_RANGE_VAL  0x062
#define VL6180X_RESULT_RANGE_STATUS 0x04D

// ── Data Structures ──

struct ExternalImuData {
    float accelX, accelY, accelZ;  // m/s²
    float gyroX, gyroY, gyroZ;    // rad/s
    float magX, magY, magZ;       // µT
    bool valid;
};

struct BnoData {
    // Quaternion orientation (hardware fusion output)
    float quatW, quatX, quatY, quatZ;
    // Euler angles (degrees)
    float heading, roll, pitch;
    // Linear acceleration (gravity removed, m/s²)
    float linAccelX, linAccelY, linAccelZ;
    // Gravity vector (m/s²)
    float gravX, gravY, gravZ;
    // Calibration: 0=uncalibrated, 3=fully calibrated per subsystem
    uint8_t calSys, calGyro, calAccel, calMag;
    bool valid;
};

static BnoData bnoData = {};
static bool bno055Present = false;

struct DistanceData {
    uint8_t rangeMm;     // 0-255mm
    bool valid;
    bool airborne;       // range > threshold = in the air
};

static ExternalImuData extImu = {};
static DistanceData distData = {};
static bool lsm9ds1Present = false;
static bool vl6180xPresent = false;

// ── I2C Helpers ──

static uint8_t i2cReadByte(uint8_t addr, uint8_t reg) {
    Wire1.beginTransmission(addr);
    Wire1.write(reg);
    Wire1.endTransmission(false);
    Wire1.requestFrom(addr, (uint8_t)1);
    if (Wire1.available()) return Wire1.read();
    return 0;
}

static void i2cWriteByte(uint8_t addr, uint8_t reg, uint8_t val) {
    Wire1.beginTransmission(addr);
    Wire1.write(reg);
    Wire1.write(val);
    Wire1.endTransmission();
}

static void i2cReadBytes(uint8_t addr, uint8_t reg, uint8_t* buf, uint8_t count) {
    Wire1.beginTransmission(addr);
    Wire1.write(reg | 0x80); // Auto-increment for LSM9DS1
    Wire1.endTransmission(false);
    Wire1.requestFrom(addr, count);
    for (uint8_t i = 0; i < count && Wire1.available(); i++) {
        buf[i] = Wire1.read();
    }
}

// VL6180X uses 16-bit register addresses
static uint8_t vl6180xReadByte(uint16_t reg) {
    Wire1.beginTransmission(VL6180X_ADDR);
    Wire1.write((reg >> 8) & 0xFF);
    Wire1.write(reg & 0xFF);
    Wire1.endTransmission(false);
    Wire1.requestFrom(VL6180X_ADDR, (uint8_t)1);
    if (Wire1.available()) return Wire1.read();
    return 0;
}

static void vl6180xWriteByte(uint16_t reg, uint8_t val) {
    Wire1.beginTransmission(VL6180X_ADDR);
    Wire1.write((reg >> 8) & 0xFF);
    Wire1.write(reg & 0xFF);
    Wire1.write(val);
    Wire1.endTransmission();
}

// ── LSM9DS1 Init & Read ──

bool initLSM9DS1() {
    uint8_t whoAmI = i2cReadByte(LSM9DS1_AG_ADDR, LSM_WHO_AM_I_AG);
    if (whoAmI != 0x68) {
        lsm9ds1Present = false;
        return false;
    }

    // Gyro: 476 Hz ODR, 2000 dps full scale
    i2cWriteByte(LSM9DS1_AG_ADDR, LSM_CTRL_REG1_G, 0xC8); // 476Hz, 2000dps
    // Accel: 476 Hz ODR, ±16g full scale
    i2cWriteByte(LSM9DS1_AG_ADDR, LSM_CTRL_REG6_XL, 0xC8); // 476Hz, ±16g
    // Block data update
    i2cWriteByte(LSM9DS1_AG_ADDR, LSM_CTRL_REG8, 0x44);

    // Magnetometer: continuous mode, medium performance, 80 Hz
    uint8_t magWho = i2cReadByte(LSM9DS1_MAG_ADDR, LSM_WHO_AM_I_M);
    if (magWho == 0x3D) {
        i2cWriteByte(LSM9DS1_MAG_ADDR, LSM_CTRL_REG1_M, 0x7C); // 80Hz, high perf
        i2cWriteByte(LSM9DS1_MAG_ADDR, LSM_CTRL_REG2_M, 0x00); // ±4 gauss
        i2cWriteByte(LSM9DS1_MAG_ADDR, LSM_CTRL_REG3_M, 0x00); // Continuous
    }

    lsm9ds1Present = true;
    return true;
}

void readLSM9DS1() {
    if (!lsm9ds1Present) { extImu.valid = false; return; }

    // Read 6 bytes accel
    uint8_t buf[6];
    i2cReadBytes(LSM9DS1_AG_ADDR, LSM_OUT_X_L_XL, buf, 6);
    int16_t rawAx = (int16_t)(buf[1] << 8 | buf[0]);
    int16_t rawAy = (int16_t)(buf[3] << 8 | buf[2]);
    int16_t rawAz = (int16_t)(buf[5] << 8 | buf[4]);

    // Read 6 bytes gyro
    i2cReadBytes(LSM9DS1_AG_ADDR, LSM_OUT_X_L_G, buf, 6);
    int16_t rawGx = (int16_t)(buf[1] << 8 | buf[0]);
    int16_t rawGy = (int16_t)(buf[3] << 8 | buf[2]);
    int16_t rawGz = (int16_t)(buf[5] << 8 | buf[4]);

    // Read 6 bytes magnetometer
    i2cReadBytes(LSM9DS1_MAG_ADDR, LSM_OUT_X_L_M, buf, 6);
    int16_t rawMx = (int16_t)(buf[1] << 8 | buf[0]);
    int16_t rawMy = (int16_t)(buf[3] << 8 | buf[2]);
    int16_t rawMz = (int16_t)(buf[5] << 8 | buf[4]);

    // Convert to physical units
    // Accel: ±16g → sensitivity = 0.732 mg/LSB
    extImu.accelX = rawAx * 0.000732f * 9.81f;
    extImu.accelY = rawAy * 0.000732f * 9.81f;
    extImu.accelZ = rawAz * 0.000732f * 9.81f;

    // Gyro: 2000 dps → sensitivity = 70 mdps/LSB → convert to rad/s
    extImu.gyroX = rawGx * 0.070f * 0.01745329f;
    extImu.gyroY = rawGy * 0.070f * 0.01745329f;
    extImu.gyroZ = rawGz * 0.070f * 0.01745329f;

    // Mag: ±4 gauss → sensitivity = 0.14 mgauss/LSB → convert to µT
    extImu.magX = rawMx * 0.014f;
    extImu.magY = rawMy * 0.014f;
    extImu.magZ = rawMz * 0.014f;

    extImu.valid = true;
}

// ── BNO055 Init & Read ──

bool initBNO055() {
    uint8_t chipId = i2cReadByte(BNO055_ADDR, BNO055_CHIP_ID);
    if (chipId != 0xA0) {
        bno055Present = false;
        return false;
    }

    // Reset
    i2cWriteByte(BNO055_ADDR, BNO055_SYS_TRIGGER, 0x20);
    delay(700);

    // Wait for chip to come back
    unsigned long start = millis();
    while (millis() - start < 1000) {
        chipId = i2cReadByte(BNO055_ADDR, BNO055_CHIP_ID);
        if (chipId == 0xA0) break;
        delay(50);
    }

    // Config mode
    i2cWriteByte(BNO055_ADDR, BNO055_OPR_MODE, BNO055_MODE_CONFIG);
    delay(25);

    // Set units: m/s², degrees, Celsius, degrees/s, Windows orientation
    i2cWriteByte(BNO055_ADDR, BNO055_UNIT_SEL, 0x00);

    // Normal power mode
    i2cWriteByte(BNO055_ADDR, 0x3E, 0x00);
    delay(10);

    // Set to NDOF mode (full 9-axis fusion)
    i2cWriteByte(BNO055_ADDR, BNO055_OPR_MODE, BNO055_MODE_NDOF);
    delay(25);

    bno055Present = true;
    return true;
}

void readBNO055() {
    if (!bno055Present) { bnoData.valid = false; return; }

    // Read quaternion (8 bytes: W, X, Y, Z as int16 LE)
    uint8_t buf[8];
    i2cReadBytes(BNO055_ADDR, BNO055_QUAT_W_LSB, buf, 8);
    int16_t qw = (int16_t)(buf[1] << 8 | buf[0]);
    int16_t qx = (int16_t)(buf[3] << 8 | buf[2]);
    int16_t qy = (int16_t)(buf[5] << 8 | buf[4]);
    int16_t qz = (int16_t)(buf[7] << 8 | buf[6]);
    // BNO055 quaternion scale: 1 quaternion unit = 1/16384
    const float QUAT_SCALE = 1.0f / 16384.0f;
    bnoData.quatW = qw * QUAT_SCALE;
    bnoData.quatX = qx * QUAT_SCALE;
    bnoData.quatY = qy * QUAT_SCALE;
    bnoData.quatZ = qz * QUAT_SCALE;

    // Read Euler angles (6 bytes: heading, roll, pitch as int16 LE)
    uint8_t eulBuf[6];
    i2cReadBytes(BNO055_ADDR, BNO055_EUL_H_LSB, eulBuf, 6);
    int16_t rawH = (int16_t)(eulBuf[1] << 8 | eulBuf[0]);
    int16_t rawR = (int16_t)(eulBuf[3] << 8 | eulBuf[2]);
    int16_t rawP = (int16_t)(eulBuf[5] << 8 | eulBuf[4]);
    // BNO055 Euler scale: 1 degree = 16 LSB
    bnoData.heading = rawH / 16.0f;
    bnoData.roll = rawR / 16.0f;
    bnoData.pitch = rawP / 16.0f;

    // Read linear acceleration (gravity removed, 6 bytes)
    uint8_t liaBuf[6];
    i2cReadBytes(BNO055_ADDR, BNO055_LIA_X_LSB, liaBuf, 6);
    int16_t liaX = (int16_t)(liaBuf[1] << 8 | liaBuf[0]);
    int16_t liaY = (int16_t)(liaBuf[3] << 8 | liaBuf[2]);
    int16_t liaZ = (int16_t)(liaBuf[5] << 8 | liaBuf[4]);
    // BNO055 accel scale: 1 m/s² = 100 LSB
    bnoData.linAccelX = liaX / 100.0f;
    bnoData.linAccelY = liaY / 100.0f;
    bnoData.linAccelZ = liaZ / 100.0f;

    // Read gravity vector (6 bytes)
    uint8_t grvBuf[6];
    i2cReadBytes(BNO055_ADDR, BNO055_GRV_X_LSB, grvBuf, 6);
    int16_t grvX = (int16_t)(grvBuf[1] << 8 | grvBuf[0]);
    int16_t grvY = (int16_t)(grvBuf[3] << 8 | grvBuf[2]);
    int16_t grvZ = (int16_t)(grvBuf[5] << 8 | grvBuf[4]);
    bnoData.gravX = grvX / 100.0f;
    bnoData.gravY = grvY / 100.0f;
    bnoData.gravZ = grvZ / 100.0f;

    // Read calibration status
    uint8_t calStat = i2cReadByte(BNO055_ADDR, BNO055_CALIB_STAT);
    bnoData.calSys = (calStat >> 6) & 0x03;
    bnoData.calGyro = (calStat >> 4) & 0x03;
    bnoData.calAccel = (calStat >> 2) & 0x03;
    bnoData.calMag = calStat & 0x03;

    bnoData.valid = true;
}

// ── VL6180X Init & Read ──

bool initVL6180X() {
    uint8_t id = vl6180xReadByte(VL6180X_ID);
    if (id != 0xB4) {
        vl6180xPresent = false;
        return false;
    }

    // Check if fresh out of reset
    if (vl6180xReadByte(VL6180X_SYSTEM_FRESH_OUT) & 0x01) {
        // Standard initialization sequence (from datasheet AN4545)
        vl6180xWriteByte(0x0207, 0x01);
        vl6180xWriteByte(0x0208, 0x01);
        vl6180xWriteByte(0x0096, 0x00);
        vl6180xWriteByte(0x0097, 0xFD);
        vl6180xWriteByte(0x00E3, 0x00);
        vl6180xWriteByte(0x00E4, 0x04);
        vl6180xWriteByte(0x00E5, 0x02);
        vl6180xWriteByte(0x00E6, 0x01);
        vl6180xWriteByte(0x00E7, 0x03);
        vl6180xWriteByte(0x00F5, 0x02);
        vl6180xWriteByte(0x00D9, 0x05);
        vl6180xWriteByte(0x00DB, 0xCE);
        vl6180xWriteByte(0x00DC, 0x03);
        vl6180xWriteByte(0x00DD, 0xF8);
        vl6180xWriteByte(0x009F, 0x00);
        vl6180xWriteByte(0x00A3, 0x3C);
        vl6180xWriteByte(0x00B7, 0x00);
        vl6180xWriteByte(0x00BB, 0x3C);
        vl6180xWriteByte(0x00B2, 0x09);
        vl6180xWriteByte(0x00CA, 0x09);
        vl6180xWriteByte(0x0198, 0x01);
        vl6180xWriteByte(0x01B0, 0x17);
        vl6180xWriteByte(0x01AD, 0x00);
        vl6180xWriteByte(0x00FF, 0x05);
        vl6180xWriteByte(0x0100, 0x05);
        vl6180xWriteByte(0x0199, 0x05);
        vl6180xWriteByte(0x01A6, 0x1B);
        vl6180xWriteByte(0x01AC, 0x3E);
        vl6180xWriteByte(0x01A7, 0x1F);
        vl6180xWriteByte(0x0030, 0x00);
        vl6180xWriteByte(VL6180X_SYSTEM_FRESH_OUT, 0x00);
    }

    // Configure for continuous ranging
    vl6180xWriteByte(0x0011, 0x10); // GPIO1 interrupt on new range
    vl6180xWriteByte(0x010A, 0x30); // Averaging period
    vl6180xWriteByte(0x003F, 0x46); // ALS integration time
    vl6180xWriteByte(0x0031, 0xFF); // Max convergence time
    vl6180xWriteByte(0x0041, 0x63); // Range check enables
    vl6180xWriteByte(0x002E, 0x01); // Continuous mode interval

    vl6180xPresent = true;
    return true;
}

void readVL6180X() {
    if (!vl6180xPresent) { distData.valid = false; return; }

    // Trigger single-shot range measurement
    vl6180xWriteByte(VL6180X_SYSRANGE_START, 0x01);

    // Wait for result (with timeout)
    unsigned long start = millis();
    while (millis() - start < 20) {
        uint8_t status = vl6180xReadByte(VL6180X_RESULT_RANGE_STATUS);
        if (status & 0x01) break; // measurement ready
    }

    distData.rangeMm = vl6180xReadByte(VL6180X_RESULT_RANGE_VAL);
    distData.valid = true;

    // Airborne if range exceeds normal boot-to-snow distance
    // Normal: ~50-150mm (boot sole height). Airborne: >200mm or max range (255)
    distData.airborne = (distData.rangeMm > 200 || distData.rangeMm == 255);
}

// ── AMG8833 Registers ──
#define AMG8833_ADDR        0x69
#define AMG8833_PCTL        0x00  // Power control
#define AMG8833_RST         0x01  // Reset
#define AMG8833_FPSC        0x02  // Frame rate
#define AMG8833_INTC        0x03  // Interrupt control
#define AMG8833_TTHL        0x0E  // Thermistor low byte
#define AMG8833_PIXEL_BASE  0x80  // Pixel data start (128 bytes: 64 pixels × 2 bytes)

struct ThermalData {
    float pixels[8][8];        // Temperature in °C per pixel
    float thermistorTemp;      // Onboard thermistor (ambient near sensor)
    float maxTemp;
    float minTemp;
    float avgTemp;
    uint8_t hotPixelCount;     // Pixels above person detection threshold
    int8_t hotCenterX;         // Center of hot region (-4 to 4, or -1 if none)
    int8_t hotCenterY;
    bool personDetected;
    bool valid;
};

static ThermalData thermalData = {};
static bool amg8833Present = false;

bool initAMG8833() {
    // Check communication by reading power control register
    Wire1.beginTransmission(AMG8833_ADDR);
    Wire1.write(AMG8833_PCTL);
    if (Wire1.endTransmission() != 0) {
        amg8833Present = false;
        return false;
    }

    // Normal mode
    i2cWriteByte(AMG8833_ADDR, AMG8833_PCTL, 0x00);
    delay(50);

    // Software reset
    i2cWriteByte(AMG8833_ADDR, AMG8833_RST, 0x3F);
    delay(10);

    // 10 FPS frame rate
    i2cWriteByte(AMG8833_ADDR, AMG8833_FPSC, 0x00);

    // Disable interrupt
    i2cWriteByte(AMG8833_ADDR, AMG8833_INTC, 0x00);

    amg8833Present = true;
    return true;
}

void readAMG8833() {
    if (!amg8833Present) { thermalData.valid = false; return; }

    // Read thermistor (2 bytes)
    uint8_t thermBuf[2];
    Wire1.beginTransmission(AMG8833_ADDR);
    Wire1.write(AMG8833_TTHL);
    Wire1.endTransmission(false);
    Wire1.requestFrom(AMG8833_ADDR, (uint8_t)2);
    thermBuf[0] = Wire1.available() ? Wire1.read() : 0;
    thermBuf[1] = Wire1.available() ? Wire1.read() : 0;
    int16_t thermRaw = (thermBuf[1] << 8) | thermBuf[0];
    if (thermRaw & 0x0800) thermRaw |= 0xF000; // Sign extend 12-bit
    thermalData.thermistorTemp = thermRaw * 0.0625f;

    // Read 64 pixels (128 bytes) in two 32-byte chunks
    uint8_t pixBuf[128];
    for (int chunk = 0; chunk < 2; chunk++) {
        uint8_t regAddr = AMG8833_PIXEL_BASE + chunk * 64;
        Wire1.beginTransmission(AMG8833_ADDR);
        Wire1.write(regAddr);
        Wire1.endTransmission(false);
        uint8_t toRead = 64;
        // I2C buffer might be limited, read in 32-byte sub-chunks
        for (int sub = 0; sub < 2; sub++) {
            Wire1.requestFrom(AMG8833_ADDR, (uint8_t)32);
            for (int i = 0; i < 32 && Wire1.available(); i++) {
                pixBuf[chunk * 64 + sub * 32 + i] = Wire1.read();
            }
        }
    }

    // Parse pixel data (each pixel is 12-bit signed, in 2 bytes LE)
    thermalData.maxTemp = -40;
    thermalData.minTemp = 200;
    float sumTemp = 0;
    thermalData.hotPixelCount = 0;
    float hotSumX = 0, hotSumY = 0;

    // Person detection threshold: anything > ambient + 10°C is likely a person
    float ambientTemp = thermalData.thermistorTemp;
    float personThreshold = ambientTemp + 10.0f;
    // On cold mountain, ambient might be -10°C, body is 25-35°C at distance
    if (personThreshold < 15.0f) personThreshold = 15.0f;

    for (int row = 0; row < 8; row++) {
        for (int col = 0; col < 8; col++) {
            int idx = (row * 8 + col) * 2;
            int16_t raw = (pixBuf[idx + 1] << 8) | pixBuf[idx];
            if (raw & 0x0800) raw |= 0xF000; // Sign extend 12-bit
            float tempC = raw * 0.25f;

            thermalData.pixels[row][col] = tempC;
            sumTemp += tempC;
            if (tempC > thermalData.maxTemp) thermalData.maxTemp = tempC;
            if (tempC < thermalData.minTemp) thermalData.minTemp = tempC;

            if (tempC > personThreshold) {
                thermalData.hotPixelCount++;
                hotSumX += col;
                hotSumY += row;
            }
        }
    }

    thermalData.avgTemp = sumTemp / 64.0f;

    // Person detection: need at least 3 hot pixels clustered together
    if (thermalData.hotPixelCount >= 3) {
        thermalData.personDetected = true;
        thermalData.hotCenterX = (int8_t)(hotSumX / thermalData.hotPixelCount - 3.5f);
        thermalData.hotCenterY = (int8_t)(hotSumY / thermalData.hotPixelCount - 3.5f);
    } else {
        thermalData.personDetected = false;
        thermalData.hotCenterX = 0;
        thermalData.hotCenterY = 0;
    }

    thermalData.valid = true;
}

/**
 * Pack thermal data for BLE transmission.
 *
 * Option A: Summary only (8 bytes) — for real-time alerts
 *   Byte 0: Person detected (bool)
 *   Byte 1: Hot pixel count
 *   Byte 2: Hot center X (int8)
 *   Byte 3: Hot center Y (int8)
 *   Byte 4: Max temp (uint8, temp+40, 0=−40°C, 255=215°C)
 *   Byte 5: Min temp (uint8, temp+40)
 *   Byte 6: Avg temp (uint8, temp+40)
 *   Byte 7: Thermistor temp (uint8, temp+40)
 *
 * Option B: Full grid (64+8 = 72 bytes) — for heatmap display
 *   Bytes 0-63: 64 pixels, each uint8 (temp+40, clamped 0-255)
 *   Bytes 64-71: Summary (same as option A)
 */
void packThermalSummary(uint8_t* out) {
    out[0] = thermalData.personDetected ? 1 : 0;
    out[1] = thermalData.hotPixelCount;
    out[2] = (uint8_t)thermalData.hotCenterX;
    out[3] = (uint8_t)thermalData.hotCenterY;
    out[4] = (uint8_t)constrain((int)(thermalData.maxTemp + 40), 0, 255);
    out[5] = (uint8_t)constrain((int)(thermalData.minTemp + 40), 0, 255);
    out[6] = (uint8_t)constrain((int)(thermalData.avgTemp + 40), 0, 255);
    out[7] = (uint8_t)constrain((int)(thermalData.thermistorTemp + 40), 0, 255);
}

void packThermalGrid(uint8_t* out) {
    for (int r = 0; r < 8; r++) {
        for (int c = 0; c < 8; c++) {
            out[r * 8 + c] = (uint8_t)constrain((int)(thermalData.pixels[r][c] + 40), 0, 255);
        }
    }
    packThermalSummary(out + 64);
}

// ── Unified Init ──

void initExternalSensors() {
    // External sensors connect via the Grove port: SDA=G32, SCL=G33
    // The internal I2C bus (G21/G22) is used by the built-in MPU6886 + RTC
    // We use Wire1 as a second I2C bus on the Grove pins
    Wire1.begin(32, 33, 400000);

    initLSM9DS1();
    initBNO055();
    initVL6180X();
    initAMG8833();
}

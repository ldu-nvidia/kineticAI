/**
 * MyCarv Thermal Vision System
 *
 * Uses AMG8833 8×8 IR thermal camera for:
 *   1. Person detection behind (heat signature against cold snow)
 *   2. Sensor fusion with mmWave radar for robust proximity alerts
 *   3. Snow surface temperature mapping
 *   4. Frostbite risk warning (boot/ambient temp monitoring)
 *   5. Indoor/outdoor detection
 *   6. Thermal heatmap display on boot LCD + phone
 */

#pragma once
#include <math.h>

// ── Thermal Analysis Results ──

struct ThermalAnalysis {
    // Person detection
    bool personBehind;
    float personBearing;       // -30 to +30 degrees (left/right in 60° FOV)
    float personElevation;     // -30 to +30 degrees (up/down)
    uint8_t personPixels;      // How many pixels are "hot"
    float personMaxTemp;       // Hottest pixel in person cluster

    // Fused detection (thermal + radar)
    uint8_t fusedConfidence;   // 0-100: confidence that a person is approaching
    bool fusedAlert;           // High confidence person approaching

    // Environment
    float snowTemp;            // Average temperature of lower pixels (snow surface)
    float ambientTemp;         // Thermistor reading
    float bootSurfaceTemp;     // Temperature near the sensor (boot proximity)

    // Safety
    bool frostbiteWarning;     // Ambient < -20°C or boot surface < -15°C
    bool indoors;              // All pixels > 10°C = probably inside

    // Heatmap colors for LCD display (8x8 RGB565 palette)
    uint16_t heatmapColors[8][8];
};

static ThermalAnalysis thermalAnalysis = {};
static unsigned long lastThermalUpdate = 0;

// ── Color Palette for Thermal Heatmap ──
// Maps temperature to color: cold=blue → warm=green → hot=red → white

uint16_t tempToColor(float tempC) {
    // Normalize to 0-1 range: -20°C to 40°C
    float normalized = (tempC + 20.0f) / 60.0f;
    if (normalized < 0) normalized = 0;
    if (normalized > 1) normalized = 1;

    uint8_t r, g, b;
    if (normalized < 0.25f) {
        // Dark blue → blue
        float t = normalized / 0.25f;
        r = 0; g = 0; b = (uint8_t)(64 + t * 191);
    } else if (normalized < 0.5f) {
        // Blue → green
        float t = (normalized - 0.25f) / 0.25f;
        r = 0; g = (uint8_t)(t * 255); b = (uint8_t)(255 * (1 - t));
    } else if (normalized < 0.75f) {
        // Green → yellow → orange
        float t = (normalized - 0.5f) / 0.25f;
        r = (uint8_t)(t * 255); g = 255; b = 0;
    } else {
        // Orange → red → white
        float t = (normalized - 0.75f) / 0.25f;
        r = 255; g = (uint8_t)(255 * (1 - t * 0.7f)); b = (uint8_t)(t * 200);
    }

    return ((r & 0xF8) << 8) | ((g & 0xFC) << 3) | (b >> 3);
}

/**
 * Run thermal analysis on the latest AMG8833 frame.
 * Call at sensor rate (~10 Hz).
 */
void updateThermalAnalysis() {
    extern ThermalData thermalData;
    extern ProximityResult proxResult;

    if (!thermalData.valid) return;

    unsigned long now = millis();
    lastThermalUpdate = now;

    float ambient = thermalData.thermistorTemp;
    float personThresh = fmaxf(15.0f, ambient + 10.0f);

    // ── Person Detection ──
    int hotCount = 0;
    float hotSumX = 0, hotSumY = 0;
    float hotMaxTemp = -40;
    float snowTempSum = 0;
    int snowPixels = 0;

    for (int r = 0; r < 8; r++) {
        for (int c = 0; c < 8; c++) {
            float t = thermalData.pixels[r][c];

            // Generate heatmap color
            thermalAnalysis.heatmapColors[r][c] = tempToColor(t);

            if (t > personThresh) {
                hotCount++;
                hotSumX += c;
                hotSumY += r;
                if (t > hotMaxTemp) hotMaxTemp = t;
            }

            // Snow temp: bottom 2 rows (pointing at ground if mounted on boot back)
            if (r >= 6) {
                snowTempSum += t;
                snowPixels++;
            }
        }
    }

    thermalAnalysis.personPixels = hotCount;
    thermalAnalysis.personMaxTemp = hotMaxTemp;

    if (hotCount >= 3) {
        thermalAnalysis.personBehind = true;
        // Convert pixel position to bearing angle (60° FOV across 8 pixels)
        float centerX = hotSumX / hotCount;
        float centerY = hotSumY / hotCount;
        thermalAnalysis.personBearing = (centerX - 3.5f) * (60.0f / 8.0f);
        thermalAnalysis.personElevation = (centerY - 3.5f) * (60.0f / 8.0f);
    } else {
        thermalAnalysis.personBehind = false;
        thermalAnalysis.personBearing = 0;
        thermalAnalysis.personElevation = 0;
    }

    // ── Snow Temperature ──
    thermalAnalysis.snowTemp = (snowPixels > 0) ? (snowTempSum / snowPixels) : ambient;
    thermalAnalysis.ambientTemp = ambient;

    // Boot surface temp: center pixels (where the sensor is closest to boot)
    thermalAnalysis.bootSurfaceTemp = (thermalData.pixels[3][3] + thermalData.pixels[3][4] +
                                       thermalData.pixels[4][3] + thermalData.pixels[4][4]) / 4.0f;

    // ── Frostbite Warning ──
    thermalAnalysis.frostbiteWarning = (ambient < -20.0f) ||
                                       (thermalAnalysis.bootSurfaceTemp < -15.0f);

    // ── Indoor Detection ──
    thermalAnalysis.indoors = (thermalData.minTemp > 10.0f);

    // ── Sensor Fusion: Thermal + Radar ──
    // Combine both detection results for higher confidence
    bool radarDetects = proxResult.targetDetected && proxResult.closingSpeedCmPerS > 20.0f;
    bool thermalDetects = thermalAnalysis.personBehind;

    if (radarDetects && thermalDetects) {
        // Both agree: very high confidence person approaching
        thermalAnalysis.fusedConfidence = 95;
        thermalAnalysis.fusedAlert = true;
    } else if (radarDetects && !thermalDetects) {
        // Radar only: could be object, not person
        thermalAnalysis.fusedConfidence = 40;
        thermalAnalysis.fusedAlert = (proxResult.distanceCm < 200); // Alert only if very close
    } else if (!radarDetects && thermalDetects) {
        // Thermal only: person detected but not necessarily approaching
        thermalAnalysis.fusedConfidence = 30;
        thermalAnalysis.fusedAlert = false;
    } else {
        thermalAnalysis.fusedConfidence = 0;
        thermalAnalysis.fusedAlert = false;
    }
}

/**
 * Draw 8×8 thermal heatmap on the M5StickC LCD.
 * Draws in a 64×64 pixel area (8 pixels × 8px each).
 */
void drawThermalHeatmap(int startX, int startY, int pixelSize) {
    for (int r = 0; r < 8; r++) {
        for (int c = 0; c < 8; c++) {
            int x = startX + c * pixelSize;
            int y = startY + r * pixelSize;
            StickCP2.Display.fillRect(x, y, pixelSize, pixelSize,
                thermalAnalysis.heatmapColors[r][c]);
        }
    }

    // Draw person detection crosshair if detected
    if (thermalAnalysis.personBehind) {
        float cx = startX + (thermalData.hotCenterX + 3.5f) * pixelSize;
        float cy = startY + (thermalData.hotCenterY + 3.5f) * pixelSize;
        StickCP2.Display.drawLine(cx - 6, cy, cx + 6, cy, TFT_WHITE);
        StickCP2.Display.drawLine(cx, cy - 6, cx, cy + 6, TFT_WHITE);
    }
}

/**
 * Pack thermal analysis for BLE (compact: 12 bytes summary + person info).
 *
 * Byte 0:  Flags (bit0=personBehind, bit1=frostbiteWarn, bit2=indoors,
 *          bit3=fusedAlert, bit4=amg8833Present)
 * Byte 1:  Person pixel count
 * Byte 2:  Person bearing (int8, degrees)
 * Byte 3:  Fused confidence (uint8, 0-100)
 * Byte 4:  Max temp (uint8, temp+40)
 * Byte 5:  Min temp (uint8, temp+40)
 * Byte 6:  Snow temp (uint8, temp+40)
 * Byte 7:  Ambient temp (uint8, temp+40)
 * Byte 8:  Boot surface temp (uint8, temp+40)
 * Byte 9:  Person max temp (uint8, temp+40)
 * Byte 10: Person elevation (int8, degrees)
 * Byte 11: Reserved
 */
void packThermalAnalysis(uint8_t* out) {
    out[0] = (thermalAnalysis.personBehind ? 0x01 : 0x00) |
             (thermalAnalysis.frostbiteWarning ? 0x02 : 0x00) |
             (thermalAnalysis.indoors ? 0x04 : 0x00) |
             (thermalAnalysis.fusedAlert ? 0x08 : 0x00) |
             (amg8833Present ? 0x10 : 0x00);
    out[1] = thermalAnalysis.personPixels;
    out[2] = (int8_t)constrain(thermalAnalysis.personBearing, -30, 30);
    out[3] = thermalAnalysis.fusedConfidence;
    out[4] = (uint8_t)constrain((int)(thermalData.maxTemp + 40), 0, 255);
    out[5] = (uint8_t)constrain((int)(thermalData.minTemp + 40), 0, 255);
    out[6] = (uint8_t)constrain((int)(thermalAnalysis.snowTemp + 40), 0, 255);
    out[7] = (uint8_t)constrain((int)(thermalAnalysis.ambientTemp + 40), 0, 255);
    out[8] = (uint8_t)constrain((int)(thermalAnalysis.bootSurfaceTemp + 40), 0, 255);
    out[9] = (uint8_t)constrain((int)(thermalAnalysis.personMaxTemp + 40), 0, 255);
    out[10] = (int8_t)constrain(thermalAnalysis.personElevation, -30, 30);
    out[11] = 0;
}

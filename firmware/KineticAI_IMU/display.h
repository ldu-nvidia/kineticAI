/**
 * KineticAI Display & Light System
 *
 * Rich LCD display with:
 *   - Key metrics (edge angle, G-force, speed proxy, turn count)
 *   - Performance emoji reactions per turn
 *   - Full-screen color flash (green = great, red = poor)
 *   - Fore-aft balance arrow
 *   - Rhythm consistency indicator
 *   - Run quality bar
 *
 * LED light shows:
 *   - Red LED flashes in patterns for fun / signaling
 *   - Turn celebration pulses
 *   - Warning patterns (lean back, low battery)
 *   - Party mode (rapid color cycling on screen for fun)
 */

#pragma once
#include <M5StickCPlus2.h>

// ── Turn Quality Tracking ──

struct TurnStats {
    float lastEdgeAngle;
    float bestEdgeAngle;
    float avgEdgeAngle;
    float lastGForce;
    float bestGForce;
    int quality;        // 0=bad, 1=ok, 2=good, 3=great, 4=epic
    int streak;         // consecutive good turns
    int bestStreak;
    unsigned long count;
    float rhythmScore;  // 0-100, how consistent turn timing is
    unsigned long lastTurnTime;
    unsigned long avgTurnDuration;
    float pitchAngle;   // fore-aft lean
};

static TurnStats ts = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

// ── Emoji Characters (UTF-8) ──
// M5StickC display supports basic ASCII. We use text representations.

static const char* EMOJI_FIRE     = "FIRE";
static const char* EMOJI_STAR     = "***";
static const char* EMOJI_CHECK    = "OK!";
static const char* EMOJI_MEH      = "meh";
static const char* EMOJI_SAD      = "...";

// ── Color Definitions ──

#define COL_BG        BLACK
#define COL_EPIC      0xFFE0   // Gold
#define COL_GREAT     0x07E0   // Green
#define COL_GOOD      0x07FF   // Cyan
#define COL_OK        0xFD20   // Orange
#define COL_BAD       0xF800   // Red
#define COL_FIRE      0xFB20   // Orange-Red
#define COL_ICE       0x867F   // Light blue
#define COL_PURPLE    0x780F   // Purple
#define COL_PINK      0xF81F   // Magenta

// ── LED Pin (shared with IR on GPIO 19) ──
#define LED_PIN 19

// ── Flash state ──
static unsigned long flashEndTime = 0;
static uint16_t flashColor = COL_BG;
static bool inFlash = false;

// ── Party mode ──
static bool partyMode = false;
static uint8_t partyHue = 0;

// ── Light pattern state ──
static unsigned long ledPatternStart = 0;
static int ledPattern = 0; // 0=off, 1=turn pulse, 2=streak celebration, 3=warning

/**
 * Classify turn quality from edge angle.
 */
int classifyTurn(float edgeAngle, float gForce) {
    float score = edgeAngle * 0.6f + gForce * 20.0f;
    if (score > 50) return 4; // epic
    if (score > 35) return 3; // great
    if (score > 22) return 2; // good
    if (score > 12) return 1; // ok
    return 0;                  // bad
}

/**
 * Get color for quality level.
 */
uint16_t qualityColor(int q) {
    switch(q) {
        case 4: return COL_EPIC;
        case 3: return COL_GREAT;
        case 2: return COL_GOOD;
        case 1: return COL_OK;
        default: return COL_BAD;
    }
}

/**
 * Get emoji text for quality level.
 */
const char* qualityEmoji(int q) {
    switch(q) {
        case 4: return EMOJI_FIRE;
        case 3: return EMOJI_STAR;
        case 2: return EMOJI_CHECK;
        case 1: return EMOJI_MEH;
        default: return EMOJI_SAD;
    }
}

/**
 * Register a completed turn and trigger visual feedback.
 */
void onTurnCompleted(float edgeAngle, float gForce, float pitch) {
    ts.count++;
    ts.lastEdgeAngle = edgeAngle;
    ts.lastGForce = gForce;
    ts.pitchAngle = pitch;
    if (edgeAngle > ts.bestEdgeAngle) ts.bestEdgeAngle = edgeAngle;
    if (gForce > ts.bestGForce) ts.bestGForce = gForce;
    ts.avgEdgeAngle = ts.avgEdgeAngle * 0.9f + edgeAngle * 0.1f;

    ts.quality = classifyTurn(edgeAngle, gForce);

    // Streak tracking
    if (ts.quality >= 2) {
        ts.streak++;
        if (ts.streak > ts.bestStreak) ts.bestStreak = ts.streak;
    } else {
        ts.streak = 0;
    }

    // Rhythm tracking
    unsigned long now = millis();
    if (ts.lastTurnTime > 0) {
        unsigned long dur = now - ts.lastTurnTime;
        if (ts.avgTurnDuration == 0) {
            ts.avgTurnDuration = dur;
        } else {
            float diff = abs((long)dur - (long)ts.avgTurnDuration);
            float consistency = 1.0f - (diff / (float)ts.avgTurnDuration);
            if (consistency < 0) consistency = 0;
            ts.rhythmScore = ts.rhythmScore * 0.8f + consistency * 100.0f * 0.2f;
            ts.avgTurnDuration = (ts.avgTurnDuration * 3 + dur) / 4;
        }
    }
    ts.lastTurnTime = now;

    // Trigger full-screen flash
    flashColor = qualityColor(ts.quality);
    flashEndTime = now + 250;
    inFlash = true;

    // LED pattern
    if (ts.streak >= 5) {
        ledPattern = 2; // celebration
    } else if (ts.quality >= 2) {
        ledPattern = 1; // pulse
    }
    ledPatternStart = now;
}

/**
 * Full-screen color flash (called from main display loop).
 * Returns true if currently flashing (caller should skip normal draw).
 */
bool drawFlashIfActive() {
    if (!inFlash) return false;
    if (millis() > flashEndTime) {
        inFlash = false;
        return false;
    }

    StickCP2.Display.fillScreen(flashColor);

    // Large emoji in center
    StickCP2.Display.setTextSize(4);
    StickCP2.Display.setTextColor(BLACK);
    StickCP2.Display.setCursor(30, 20);
    StickCP2.Display.printf("%s", qualityEmoji(ts.quality));

    // Edge angle big
    StickCP2.Display.setTextSize(3);
    StickCP2.Display.setCursor(30, 70);
    StickCP2.Display.printf("%2.0f", ts.lastEdgeAngle);
    StickCP2.Display.setTextSize(1);
    StickCP2.Display.printf("deg");

    // Streak
    if (ts.streak >= 3) {
        StickCP2.Display.setTextSize(2);
        StickCP2.Display.setCursor(30, 105);
        StickCP2.Display.printf("x%d STREAK!", ts.streak);
    }

    return true;
}

/**
 * Draw the main live data screen with all metrics and fun elements.
 */
void drawLiveScreen(float* packet, bool bleConn, uint32_t recCount) {
    // Check for flash overlay
    if (drawFlashIfActive()) return;

    // Proximity danger overlay takes priority
    extern ProximityResult proxResult;
    if (proxResult.level >= PROX_WARNING) {
        uint16_t bgCol = proximityLevelColor(proxResult.level);
        StickCP2.Display.fillScreen(bgCol);
        StickCP2.Display.setTextSize(3);
        StickCP2.Display.setTextColor(BLACK);
        StickCP2.Display.setCursor(10, 20);
        StickCP2.Display.printf("%s", proximityLevelStr(proxResult.level));
        StickCP2.Display.setTextSize(4);
        StickCP2.Display.setCursor(10, 60);
        StickCP2.Display.printf("%.1fm", proxResult.distanceCm / 100.0f);
        StickCP2.Display.setTextSize(2);
        StickCP2.Display.setCursor(10, 105);
        StickCP2.Display.printf("%.0f cm/s", proxResult.closingSpeedCmPerS);
        return;
    }

    // Party mode: cycling background
    if (partyMode) {
        uint16_t bg = colorFromHue(partyHue++);
        StickCP2.Display.fillScreen(bg);
    } else {
        StickCP2.Display.fillScreen(COL_BG);
    }

    float edgeAngle = fabsf(atan2f(packet[0],
        sqrtf(packet[1]*packet[1] + packet[2]*packet[2]))) * 57.296f;
    float gForce = sqrtf(packet[0]*packet[0] + packet[1]*packet[1] + packet[2]*packet[2]) / 9.81f;
    float pitch = atan2f(packet[1], sqrtf(packet[0]*packet[0] + packet[2]*packet[2])) * 57.296f;
    ts.pitchAngle = pitch;

    int y = 2;

    // ── Row 1: Side + Status + Battery ──
    StickCP2.Display.setTextSize(2);
    StickCP2.Display.setTextColor(bleConn ? TFT_GREEN : TFT_CYAN);
    StickCP2.Display.setCursor(5, y);
    StickCP2.Display.printf("%c", BOOT_SIDE);

    // Connection dot
    StickCP2.Display.fillCircle(30, y + 7, 5, bleConn ? TFT_GREEN : TFT_RED);

    // Last turn emoji
    if (ts.count > 0) {
        StickCP2.Display.setTextColor(qualityColor(ts.quality));
        StickCP2.Display.setCursor(80, y);
        StickCP2.Display.printf("%s", qualityEmoji(ts.quality));
    }

    // Battery
    float batV = StickCP2.Power.getBatteryVoltage() / 1000.0f;
    int batPct = constrain((int)((batV - 3.2f) / 0.9f * 100), 0, 100);
    StickCP2.Display.setTextSize(1);
    StickCP2.Display.setTextColor(batPct > 20 ? TFT_GREEN : TFT_RED);
    StickCP2.Display.setCursor(190, y + 2);
    StickCP2.Display.printf("%d%%", batPct);

    // Recording indicator
    if (recCount > 0) {
        StickCP2.Display.fillCircle(180, y + 5, 3, TFT_RED);
    }

    y += 22;

    // ── Row 2: EDGE ANGLE (big, with color) ──
    uint16_t edgeCol = qualityColor(classifyTurn(edgeAngle, gForce));
    StickCP2.Display.setTextSize(4);
    StickCP2.Display.setTextColor(edgeCol);
    StickCP2.Display.setCursor(5, y);
    StickCP2.Display.printf("%4.1f", edgeAngle);
    StickCP2.Display.setTextSize(1);
    StickCP2.Display.setTextColor(TFT_WHITE);
    StickCP2.Display.setCursor(140, y + 5);
    StickCP2.Display.printf("EDGE");
    StickCP2.Display.setCursor(140, y + 18);
    StickCP2.Display.printf("deg");

    // Edge angle bar (horizontal, fills from left)
    y += 35;
    int barW = (int)(edgeAngle / 60.0f * 200);
    if (barW > 200) barW = 200;
    StickCP2.Display.fillRect(5, y, barW, 6, edgeCol);
    StickCP2.Display.drawRect(5, y, 200, 6, TFT_DARKGREY);

    y += 12;

    // ── Row 3: G-Force + Fore-Aft Arrow ──
    StickCP2.Display.setTextSize(2);
    StickCP2.Display.setTextColor(gForce > 2.0f ? COL_FIRE : (gForce > 1.2f ? COL_GOOD : TFT_WHITE));
    StickCP2.Display.setCursor(5, y);
    StickCP2.Display.printf("%.1fG", gForce);

    // Fore-aft balance arrow
    StickCP2.Display.setTextSize(2);
    if (pitch > 8) {
        // Leaning back (bad)
        StickCP2.Display.setTextColor(TFT_RED);
        StickCP2.Display.setCursor(90, y);
        StickCP2.Display.printf(">> FWD!");
    } else if (pitch < -8) {
        // Leaning too far forward
        StickCP2.Display.setTextColor(COL_OK);
        StickCP2.Display.setCursor(90, y);
        StickCP2.Display.printf("<< BACK");
    } else {
        // Centered (good)
        StickCP2.Display.setTextColor(TFT_GREEN);
        StickCP2.Display.setCursor(90, y);
        StickCP2.Display.printf("CENTERED");
    }

    y += 22;

    // ── Row 4: Turn Count + Streak + Rhythm ──
    StickCP2.Display.setTextSize(2);
    StickCP2.Display.setTextColor(TFT_YELLOW);
    StickCP2.Display.setCursor(5, y);
    StickCP2.Display.printf("T:%lu", ts.count);

    if (ts.streak >= 3) {
        StickCP2.Display.setTextColor(COL_EPIC);
        StickCP2.Display.printf(" x%d", ts.streak);
    }

    // Rhythm dots (last 6 turns' consistency)
    if (ts.rhythmScore > 0) {
        int rx = 160;
        StickCP2.Display.setTextSize(1);
        StickCP2.Display.setTextColor(TFT_WHITE);
        StickCP2.Display.setCursor(rx, y + 2);
        StickCP2.Display.printf("R:");
        // Draw rhythm as small colored squares
        uint16_t rCol = ts.rhythmScore > 70 ? TFT_GREEN :
                        (ts.rhythmScore > 40 ? COL_OK : TFT_RED);
        int rBarW = (int)(ts.rhythmScore / 100.0f * 30);
        StickCP2.Display.fillRect(rx + 15, y + 4, rBarW, 8, rCol);
        StickCP2.Display.drawRect(rx + 15, y + 4, 30, 8, TFT_DARKGREY);
    }

    y += 22;

    // ── Row 5: Run Quality Bar (overall) ──
    float runQuality = 0;
    if (ts.count > 0) {
        runQuality = ts.avgEdgeAngle / 45.0f * 50.0f +
                     ts.rhythmScore * 0.3f +
                     (float)ts.streak / 10.0f * 20.0f;
        if (runQuality > 100) runQuality = 100;
    }

    StickCP2.Display.setTextSize(1);
    StickCP2.Display.setTextColor(TFT_WHITE);
    StickCP2.Display.setCursor(5, y);
    StickCP2.Display.printf("RUN ");

    uint16_t rqCol = runQuality > 70 ? COL_GREAT :
                     (runQuality > 40 ? COL_OK : COL_BAD);
    int rqW = (int)(runQuality / 100.0f * 150);
    StickCP2.Display.fillRect(35, y, rqW, 10, rqCol);
    StickCP2.Display.drawRect(35, y, 150, 10, TFT_DARKGREY);

    StickCP2.Display.setCursor(190, y);
    StickCP2.Display.printf("%d", (int)runQuality);

    y += 16;

    // ── Row 6: Snow Type + Carve Quality (from mic) ──
    StickCP2.Display.setTextSize(1);
    // These reference extern from mic_analysis.h
    extern MicAnalysisResult micResult;

    if (micResult.snowType != SNOW_UNKNOWN) {
        StickCP2.Display.setTextColor(snowTypeColor(micResult.snowType));
        StickCP2.Display.setCursor(5, y);
        StickCP2.Display.printf("%s", snowTypeStr(micResult.snowType));

        StickCP2.Display.setTextColor(carveQualityColor(micResult.carveQuality));
        StickCP2.Display.setCursor(90, y);
        StickCP2.Display.printf("%s", carveQualityStr(micResult.carveQuality));
    } else if (ts.streak >= 5) {
        StickCP2.Display.setTextColor(COL_EPIC);
        StickCP2.Display.setCursor(5, y);
        StickCP2.Display.printf("x%d STREAK!", ts.streak);
    } else if (ts.count > 0 && ts.quality >= 3) {
        StickCP2.Display.setTextColor(COL_GREAT);
        StickCP2.Display.setCursor(5, y);
        StickCP2.Display.printf("Crushing it!");
    } else {
        StickCP2.Display.setTextColor(TFT_DARKGREY);
        StickCP2.Display.setCursor(5, y);
        StickCP2.Display.printf("BtnA:WiFi BtnB:Mark");
    }
}

/**
 * Update the LED (GPIO 19) based on current pattern.
 * Call this from the main loop at high frequency.
 *
 * The red LED doubles as a rear-facing signal visible to skiers behind you.
 */
void updateLed() {
    unsigned long elapsed = millis() - ledPatternStart;

    switch (ledPattern) {
        case 0: // off
            digitalWrite(LED_PIN, LOW);
            break;

        case 1: // turn pulse: single 100ms blink
            if (elapsed < 100) {
                digitalWrite(LED_PIN, HIGH);
            } else {
                digitalWrite(LED_PIN, LOW);
                ledPattern = 0;
            }
            break;

        case 2: // streak celebration: rapid triple blink
            if (elapsed < 50 || (elapsed > 100 && elapsed < 150) || (elapsed > 200 && elapsed < 250)) {
                digitalWrite(LED_PIN, HIGH);
            } else if (elapsed > 300) {
                digitalWrite(LED_PIN, LOW);
                ledPattern = 0;
            } else {
                digitalWrite(LED_PIN, LOW);
            }
            break;

        case 3: // warning: slow double pulse (lean back warning)
            if (elapsed < 200 || (elapsed > 400 && elapsed < 600)) {
                digitalWrite(LED_PIN, HIGH);
            } else if (elapsed > 800) {
                digitalWrite(LED_PIN, LOW);
                ledPattern = 0;
            } else {
                digitalWrite(LED_PIN, LOW);
            }
            break;

        case 4: // party: rapid strobe
            digitalWrite(LED_PIN, (elapsed / 50) % 2 ? HIGH : LOW);
            if (elapsed > 2000) {
                ledPattern = 0;
                digitalWrite(LED_PIN, LOW);
            }
            break;

        case 5: // braking signal: steady on (like a brake light when slowing)
            if (elapsed < 1500) {
                digitalWrite(LED_PIN, HIGH);
            } else {
                digitalWrite(LED_PIN, LOW);
                ledPattern = 0;
            }
            break;
    }
}

/**
 * Check if the skier is braking hard (sudden deceleration).
 * Triggers a "brake light" LED pattern visible to skiers behind.
 */
void checkBrakingSignal(float gyroZ, float prevGyroZ) {
    float decel = fabsf(gyroZ) - fabsf(prevGyroZ);
    if (decel < -1.5f && ledPattern == 0) {
        ledPattern = 5; // brake light
        ledPatternStart = millis();
    }
}

/**
 * Check fore-aft balance and trigger warning if leaning back too long.
 */
static unsigned long leanBackStart = 0;
void checkBalanceWarning(float pitch) {
    if (pitch > 12) {
        if (leanBackStart == 0) leanBackStart = millis();
        if (millis() - leanBackStart > 2000 && ledPattern == 0) {
            ledPattern = 3; // warning
            ledPatternStart = millis();
            leanBackStart = millis(); // reset so it doesn't spam
        }
    } else {
        leanBackStart = 0;
    }
}

/**
 * Toggle party mode (Button A long press or special gesture).
 */
void togglePartyMode() {
    partyMode = !partyMode;
    if (partyMode) {
        ledPattern = 4; // strobe
        ledPatternStart = millis();
    }
}

/**
 * Convert a hue value (0-255) to a 16-bit RGB565 color for party mode.
 */
uint16_t colorFromHue(uint8_t hue) {
    uint8_t r, g, b;
    uint8_t region = hue / 43;
    uint8_t remainder = (hue - region * 43) * 6;
    switch (region) {
        case 0:  r = 255; g = remainder; b = 0; break;
        case 1:  r = 255 - remainder; g = 255; b = 0; break;
        case 2:  r = 0; g = 255; b = remainder; break;
        case 3:  r = 0; g = 255 - remainder; b = 255; break;
        case 4:  r = remainder; g = 0; b = 255; break;
        default: r = 255; g = 0; b = 255 - remainder; break;
    }
    return ((r & 0xF8) << 8) | ((g & 0xFC) << 3) | (b >> 3);
}

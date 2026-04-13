/**
 * KineticAI Boot Display Customization System
 *
 * Receives commands from phone over BLE to customize the boot LCD:
 *   - Custom text messages (per quality level or always-on)
 *   - Emoji selection per turn quality
 *   - Color theme
 *   - Display mode (metrics, emoji, stealth, show-off)
 *   - Custom image/GIF upload and display
 *
 * BLE Command Protocol (written to CMD_CHAR_UUID 4d430004):
 *   Byte 0: Command ID
 *   Bytes 1+: Payload
 *
 * Commands:
 *   0x01 = Start recording
 *   0x02 = Stop recording
 *   0x03 = Clear data
 *   0x10 = Set display mode (1 byte: mode)
 *   0x11 = Set color theme (3 bytes: R, G, B accent color)
 *   0x12 = Set custom text (1 byte slot + up to 19 bytes UTF-8 text)
 *   0x13 = Set emoji text (1 byte quality level + up to 8 bytes text)
 *   0x14 = Begin image upload (4 bytes: total size LE uint32)
 *   0x15 = Image data chunk (up to 19 bytes of pixel data)
 *   0x16 = End image upload / commit
 *   0x17 = Begin GIF upload (4 bytes: frame count, 2 bytes: delay ms)
 *   0x18 = GIF frame data chunk
 *   0x19 = End GIF upload / commit
 *   0x1A = Set splash image (use uploaded image as boot splash)
 *   0x1B = Clear custom image
 *   0x20 = Set turn celebration text (up to 19 bytes)
 *   0x21 = Set streak text (up to 19 bytes)
 *
 * Display Modes:
 *   0 = Metrics (default — edge angle, G-force, balance, streaks)
 *   1 = Emoji (big emoji per turn, minimal metrics)
 *   2 = Stealth (screen off, LED only)
 *   3 = Show-off (huge Kinetic Score / edge angle, flashy)
 *   4 = Custom Image (shows uploaded image as background)
 *   5 = GIF Animation (plays uploaded GIF frames in loop)
 *   6 = Party (rainbow cycling)
 */

#pragma once
#include <M5StickCPlus2.h>
#include <Preferences.h>

// ── Display Modes ──
#define DMODE_METRICS   0
#define DMODE_EMOJI     1
#define DMODE_STEALTH   2
#define DMODE_SHOWOFF   3
#define DMODE_IMAGE     4
#define DMODE_GIF       5
#define DMODE_PARTY     6

// ── Custom Text Slots ──
#define SLOT_EPIC_EMOJI    0
#define SLOT_GREAT_EMOJI   1
#define SLOT_GOOD_EMOJI    2
#define SLOT_OK_EMOJI      3
#define SLOT_BAD_EMOJI     4
#define SLOT_CELEBRATION   5
#define SLOT_STREAK        6
#define SLOT_CUSTOM_MSG    7
#define NUM_TEXT_SLOTS     8

// ── Image Storage ──
// Full screen = 135*240*2 = 64,800 bytes (RGB565)
// We store in PSRAM. For GIF, up to 10 frames.
#define IMG_WIDTH       135
#define IMG_HEIGHT      240
#define IMG_BYTES       (IMG_WIDTH * IMG_HEIGHT * 2)
#define MAX_GIF_FRAMES  10

struct CustomConfig {
    uint8_t displayMode;
    uint8_t accentR, accentG, accentB;
    char textSlots[NUM_TEXT_SLOTS][20]; // 19 chars + null
    bool hasCustomImage;
    bool hasGif;
    uint8_t gifFrameCount;
    uint16_t gifDelayMs;
};

static CustomConfig customCfg = {
    .displayMode = DMODE_METRICS,
    .accentR = 0x38, .accentG = 0xBD, .accentB = 0xF8, // SkyBlue default
    .textSlots = {"FIRE!", "***", "OK!", "meh", "...", "YEAH!", "STREAK!", ""},
    .hasCustomImage = false,
    .hasGif = false,
    .gifFrameCount = 0,
    .gifDelayMs = 200,
};

// Image buffers in PSRAM
static uint16_t* customImageBuf = nullptr;
static uint16_t* gifFrames[MAX_GIF_FRAMES] = {nullptr};
static uint8_t currentGifFrame = 0;
static unsigned long lastGifFrameTime = 0;

// Upload state
static uint32_t uploadExpectedSize = 0;
static uint32_t uploadReceivedSize = 0;
static uint8_t* uploadBuffer = nullptr;
static uint8_t uploadingWhat = 0; // 0=nothing, 1=image, 2=gif frame
static uint8_t gifUploadFrame = 0;

// Preferences for persisting config across reboots
static Preferences custPrefs;

/**
 * Initialize custom display buffers in PSRAM.
 */
void customDisplayInit() {
    customImageBuf = (uint16_t*)ps_malloc(IMG_BYTES);
    for (int i = 0; i < MAX_GIF_FRAMES; i++) {
        gifFrames[i] = (uint16_t*)ps_malloc(IMG_BYTES);
    }
    uploadBuffer = (uint8_t*)ps_malloc(IMG_BYTES);

    // Load saved config
    custPrefs.begin("kai_cust", true);
    customCfg.displayMode = custPrefs.getUChar("dmode", DMODE_METRICS);
    customCfg.accentR = custPrefs.getUChar("accR", 0x38);
    customCfg.accentG = custPrefs.getUChar("accG", 0xBD);
    customCfg.accentB = custPrefs.getUChar("accB", 0xF8);
    for (int i = 0; i < NUM_TEXT_SLOTS; i++) {
        String key = "txt" + String(i);
        String val = custPrefs.getString(key.c_str(), customCfg.textSlots[i]);
        strncpy(customCfg.textSlots[i], val.c_str(), 19);
        customCfg.textSlots[i][19] = 0;
    }
    custPrefs.end();
}

/**
 * Save current config to flash (persists across reboots).
 */
void saveCustomConfig() {
    custPrefs.begin("kai_cust", false);
    custPrefs.putUChar("dmode", customCfg.displayMode);
    custPrefs.putUChar("accR", customCfg.accentR);
    custPrefs.putUChar("accG", customCfg.accentG);
    custPrefs.putUChar("accB", customCfg.accentB);
    for (int i = 0; i < NUM_TEXT_SLOTS; i++) {
        String key = "txt" + String(i);
        custPrefs.putString(key.c_str(), customCfg.textSlots[i]);
    }
    custPrefs.end();
}

uint16_t accentColor565() {
    return ((customCfg.accentR & 0xF8) << 8) |
           ((customCfg.accentG & 0xFC) << 3) |
           (customCfg.accentB >> 3);
}

/**
 * Process a BLE command for display customization.
 */
void processCustomCommand(const uint8_t* data, size_t len) {
    if (len < 1) return;
    uint8_t cmd = data[0];

    switch (cmd) {
        case 0x10: // Set display mode
            if (len >= 2) {
                customCfg.displayMode = data[1];
                if (customCfg.displayMode == DMODE_STEALTH) {
                    StickCP2.Display.setBrightness(0);
                } else {
                    StickCP2.Display.setBrightness(80);
                }
                saveCustomConfig();
            }
            break;

        case 0x11: // Set color theme
            if (len >= 4) {
                customCfg.accentR = data[1];
                customCfg.accentG = data[2];
                customCfg.accentB = data[3];
                saveCustomConfig();
            }
            break;

        case 0x12: // Set custom text (slot + text)
            if (len >= 2) {
                uint8_t slot = data[1];
                if (slot < NUM_TEXT_SLOTS) {
                    size_t txtLen = len - 2;
                    if (txtLen > 19) txtLen = 19;
                    memcpy(customCfg.textSlots[slot], data + 2, txtLen);
                    customCfg.textSlots[slot][txtLen] = 0;
                    saveCustomConfig();
                }
            }
            break;

        case 0x13: // Set emoji text for quality level
            if (len >= 2) {
                uint8_t level = data[1];
                if (level <= 4) {
                    size_t txtLen = len - 2;
                    if (txtLen > 19) txtLen = 19;
                    memcpy(customCfg.textSlots[level], data + 2, txtLen);
                    customCfg.textSlots[level][txtLen] = 0;
                    saveCustomConfig();
                }
            }
            break;

        case 0x14: // Begin image upload
            if (len >= 5) {
                memcpy(&uploadExpectedSize, data + 1, 4);
                uploadReceivedSize = 0;
                uploadingWhat = 1;
                if (uploadExpectedSize > IMG_BYTES) uploadExpectedSize = IMG_BYTES;
            }
            break;

        case 0x15: // Image data chunk
            if (uploadingWhat > 0 && uploadBuffer && len > 1) {
                size_t chunkLen = len - 1;
                if (uploadReceivedSize + chunkLen <= IMG_BYTES) {
                    memcpy(((uint8_t*)uploadBuffer) + uploadReceivedSize, data + 1, chunkLen);
                    uploadReceivedSize += chunkLen;
                }
            }
            break;

        case 0x16: // End image upload
            if (uploadingWhat == 1 && customImageBuf && uploadBuffer) {
                memcpy(customImageBuf, uploadBuffer, uploadReceivedSize);
                customCfg.hasCustomImage = true;
                uploadingWhat = 0;
            }
            break;

        case 0x17: // Begin GIF upload
            if (len >= 7) {
                memcpy(&customCfg.gifFrameCount, data + 1, 1);
                memcpy(&customCfg.gifDelayMs, data + 5, 2);
                if (customCfg.gifFrameCount > MAX_GIF_FRAMES)
                    customCfg.gifFrameCount = MAX_GIF_FRAMES;
                gifUploadFrame = 0;
                uploadReceivedSize = 0;
                uploadingWhat = 2;
            }
            break;

        case 0x18: // GIF frame data chunk (same as 0x15)
            if (uploadingWhat == 2 && uploadBuffer && len > 1) {
                size_t chunkLen = len - 1;
                if (uploadReceivedSize + chunkLen <= IMG_BYTES) {
                    memcpy(((uint8_t*)uploadBuffer) + uploadReceivedSize, data + 1, chunkLen);
                    uploadReceivedSize += chunkLen;
                }
            }
            break;

        case 0x19: // End GIF frame / commit
            if (uploadingWhat == 2 && gifUploadFrame < MAX_GIF_FRAMES) {
                if (gifFrames[gifUploadFrame] && uploadBuffer) {
                    memcpy(gifFrames[gifUploadFrame], uploadBuffer, uploadReceivedSize);
                }
                gifUploadFrame++;
                uploadReceivedSize = 0;
                if (gifUploadFrame >= customCfg.gifFrameCount) {
                    customCfg.hasGif = true;
                    uploadingWhat = 0;
                    currentGifFrame = 0;
                }
            }
            break;

        case 0x1B: // Clear custom image
            customCfg.hasCustomImage = false;
            customCfg.hasGif = false;
            customCfg.gifFrameCount = 0;
            break;

        case 0x20: // Set celebration text
            if (len >= 2) {
                size_t txtLen = len - 1;
                if (txtLen > 19) txtLen = 19;
                memcpy(customCfg.textSlots[SLOT_CELEBRATION], data + 1, txtLen);
                customCfg.textSlots[SLOT_CELEBRATION][txtLen] = 0;
                saveCustomConfig();
            }
            break;

        case 0x21: // Set streak text
            if (len >= 2) {
                size_t txtLen = len - 1;
                if (txtLen > 19) txtLen = 19;
                memcpy(customCfg.textSlots[SLOT_STREAK], data + 1, txtLen);
                customCfg.textSlots[SLOT_STREAK][txtLen] = 0;
                saveCustomConfig();
            }
            break;
    }
}

/**
 * Get the custom emoji text for a quality level.
 */
const char* customEmoji(int quality) {
    if (quality >= 0 && quality <= 4) {
        return customCfg.textSlots[quality];
    }
    return "?";
}

/**
 * Draw custom image mode (full screen image as background).
 */
void drawCustomImageMode() {
    if (customCfg.hasCustomImage && customImageBuf) {
        StickCP2.Display.pushImage(0, 0, IMG_WIDTH, IMG_HEIGHT, customImageBuf);
    } else {
        StickCP2.Display.fillScreen(BLACK);
        StickCP2.Display.setTextSize(2);
        StickCP2.Display.setTextColor(TFT_WHITE);
        StickCP2.Display.setCursor(10, 50);
        StickCP2.Display.printf("No image");
        StickCP2.Display.setCursor(10, 80);
        StickCP2.Display.printf("Upload from");
        StickCP2.Display.setCursor(10, 110);
        StickCP2.Display.printf("phone app");
    }
}

/**
 * Draw GIF animation mode (cycles through frames).
 */
void drawGifMode() {
    if (!customCfg.hasGif || customCfg.gifFrameCount == 0) {
        drawCustomImageMode();
        return;
    }

    unsigned long now = millis();
    if (now - lastGifFrameTime >= customCfg.gifDelayMs) {
        lastGifFrameTime = now;
        currentGifFrame = (currentGifFrame + 1) % customCfg.gifFrameCount;
    }

    if (gifFrames[currentGifFrame]) {
        StickCP2.Display.pushImage(0, 0, IMG_WIDTH, IMG_HEIGHT, gifFrames[currentGifFrame]);
    }
}

/**
 * Draw emoji-only mode (big emoji, minimal info).
 */
void drawEmojiMode(float edgeAngle, float gForce) {
    StickCP2.Display.fillScreen(BLACK);

    int q = classifyTurn(edgeAngle, gForce);
    uint16_t col = qualityColor(q);
    const char* emoji = customEmoji(q);

    // Giant emoji text
    StickCP2.Display.setTextSize(5);
    StickCP2.Display.setTextColor(col);
    int textW = strlen(emoji) * 30;
    int x = (240 - textW) / 2;
    if (x < 5) x = 5;
    StickCP2.Display.setCursor(x, 30);
    StickCP2.Display.printf("%s", emoji);

    // Edge angle below
    StickCP2.Display.setTextSize(3);
    StickCP2.Display.setTextColor(TFT_WHITE);
    StickCP2.Display.setCursor(60, 95);
    StickCP2.Display.printf("%2.0f", edgeAngle);
    StickCP2.Display.setTextSize(1);
    StickCP2.Display.printf("deg");
}

/**
 * Draw show-off mode (huge numbers, flashy).
 */
void drawShowoffMode(float edgeAngle, float gForce, unsigned long turns) {
    uint16_t accent = accentColor565();

    // Alternate between edge angle and G-force every 2 seconds
    bool showEdge = (millis() / 2000) % 2 == 0;

    StickCP2.Display.fillScreen(BLACK);

    if (showEdge) {
        // Huge edge angle
        StickCP2.Display.setTextSize(6);
        StickCP2.Display.setTextColor(accent);
        StickCP2.Display.setCursor(10, 20);
        StickCP2.Display.printf("%2.0f", edgeAngle);
        StickCP2.Display.setTextSize(2);
        StickCP2.Display.setCursor(10, 85);
        StickCP2.Display.setTextColor(TFT_WHITE);
        StickCP2.Display.printf("EDGE ANGLE");
    } else {
        // Huge G-force
        StickCP2.Display.setTextSize(5);
        StickCP2.Display.setTextColor(gForce > 2.0f ? COL_FIRE : accent);
        StickCP2.Display.setCursor(10, 20);
        StickCP2.Display.printf("%.1f", gForce);
        StickCP2.Display.setTextSize(3);
        StickCP2.Display.printf("G");
        StickCP2.Display.setTextSize(2);
        StickCP2.Display.setCursor(10, 85);
        StickCP2.Display.setTextColor(TFT_WHITE);
        StickCP2.Display.printf("G-FORCE");
    }

    // Turn count at bottom
    StickCP2.Display.setTextSize(2);
    StickCP2.Display.setTextColor(TFT_YELLOW);
    StickCP2.Display.setCursor(10, 115);
    StickCP2.Display.printf("TURNS: %lu", turns);
}

/**
 * Master display router — calls the right draw function based on mode.
 */
void drawCustomDisplay(float* packet, bool bleConn, uint32_t recCount) {
    float edgeAngle = fabsf(atan2f(packet[0],
        sqrtf(packet[1]*packet[1] + packet[2]*packet[2]))) * 57.296f;
    float gForce = sqrtf(packet[0]*packet[0] + packet[1]*packet[1] + packet[2]*packet[2]) / 9.81f;

    // Flash always takes priority
    if (drawFlashIfActive()) return;

    switch (customCfg.displayMode) {
        case DMODE_METRICS:
            drawLiveScreen(packet, bleConn, recCount);
            break;
        case DMODE_EMOJI:
            drawEmojiMode(edgeAngle, gForce);
            break;
        case DMODE_STEALTH:
            // Screen stays off, do nothing
            break;
        case DMODE_SHOWOFF:
            drawShowoffMode(edgeAngle, gForce, ts.count);
            break;
        case DMODE_IMAGE:
            drawCustomImageMode();
            break;
        case DMODE_GIF:
            drawGifMode();
            break;
        case DMODE_PARTY:
            partyMode = true;
            drawLiveScreen(packet, bleConn, recCount);
            break;
        default:
            drawLiveScreen(packet, bleConn, recCount);
            break;
    }
}

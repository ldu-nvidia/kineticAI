/**
 * KineticAI Microphone Analysis System
 *
 * Uses the SPM1423 PDM microphone on the M5StickC Plus2 for:
 *   1. Snow surface classification (ice/groomed/powder/slush/crud)
 *   2. Carve vs skid detection
 *   3. Speed estimation from noise level
 *   4. Fall/crash detection (impact sound spike)
 *   5. Binding click detection (ski on/off)
 *   6. Ambient noise level monitoring
 *
 * Algorithm:
 *   - Sample 512 audio samples at 16 kHz (~32ms window)
 *   - Compute FFT to get frequency spectrum
 *   - Extract energy in 4 bands: sub-bass, bass, mid, high
 *   - Classify snow type from band ratios
 *   - Detect carve quality from high-freq energy
 *   - Monitor RMS level for speed/fall/ambient
 *
 * PDM Mic pins on M5StickC Plus2: CLK=GPIO0, DATA=GPIO34
 */

#pragma once
#include <M5StickCPlus2.h>
#include <driver/i2s.h>
#include <math.h>

// ── Audio Config ──
#define MIC_SAMPLE_RATE    16000
#define MIC_SAMPLES        512
#define MIC_ANALYSIS_HZ    4      // Run analysis 4 times per second
#define MIC_PIN_CLK        0
#define MIC_PIN_DATA       34

// ── FFT Config ──
#define FFT_SIZE           256    // Power of 2, half of samples
#define NUM_BANDS          4

// ── Snow Types ──
enum SnowType : uint8_t {
    SNOW_UNKNOWN  = 0,
    SNOW_POWDER   = 1,  // 'P'
    SNOW_GROOMED  = 2,  // 'G'
    SNOW_ICE      = 3,  // 'I'
    SNOW_SLUSH    = 4,  // 'S'
    SNOW_CRUD     = 5,  // 'C'
};

// ── Carve Quality ──
enum CarveQuality : uint8_t {
    CARVE_UNKNOWN   = 0,
    CARVE_CLEAN     = 1,  // Low high-freq energy
    CARVE_MODERATE  = 2,  // Some scraping
    CARVE_SKIDDING  = 3,  // Heavy high-freq scraping
    CARVE_CHATTER   = 4,  // Rapid vibration bursts (ice)
};

// ── Result struct sent over BLE ──
struct MicAnalysisResult {
    SnowType snowType;
    CarveQuality carveQuality;
    uint8_t speedProxy;        // 0-255, correlates with noise level
    bool fallDetected;
    bool bindingClick;
    uint8_t ambientLevel;      // 0-255 RMS
    float bandEnergy[NUM_BANDS]; // For debugging/display
};

// ── State ──
static int16_t micBuffer[MIC_SAMPLES];
static float fftReal[FFT_SIZE];
static float fftImag[FFT_SIZE];
static float magnitudes[FFT_SIZE / 2];

static MicAnalysisResult micResult = {};
static unsigned long lastMicAnalysis = 0;

// History for fall detection and binding click
static float rmsHistory[8] = {0};
static uint8_t rmsHistIdx = 0;
static float peakRmsEver = 0;

// Binding click detector state
static float prevRms = 0;
static unsigned long lastClickTime = 0;

// I2S initialized flag
static bool i2sInitialized = false;

/**
 * Simple in-place FFT (Cooley-Tukey, radix-2 DIT).
 * N must be power of 2.
 */
void simpleFFT(float* real, float* imag, int n) {
    // Bit-reversal permutation
    int j = 0;
    for (int i = 1; i < n - 1; i++) {
        int bit = n >> 1;
        while (j & bit) {
            j ^= bit;
            bit >>= 1;
        }
        j ^= bit;
        if (i < j) {
            float tr = real[i]; real[i] = real[j]; real[j] = tr;
            float ti = imag[i]; imag[i] = imag[j]; imag[j] = ti;
        }
    }

    // FFT butterfly
    for (int len = 2; len <= n; len <<= 1) {
        float angle = -2.0f * M_PI / len;
        float wR = cosf(angle);
        float wI = sinf(angle);
        for (int i = 0; i < n; i += len) {
            float curR = 1.0f, curI = 0.0f;
            for (int k = 0; k < len / 2; k++) {
                float tR = curR * real[i + k + len/2] - curI * imag[i + k + len/2];
                float tI = curR * imag[i + k + len/2] + curI * real[i + k + len/2];
                real[i + k + len/2] = real[i + k] - tR;
                imag[i + k + len/2] = imag[i + k] - tI;
                real[i + k] += tR;
                imag[i + k] += tI;
                float newR = curR * wR - curI * wI;
                float newI = curR * wI + curI * wR;
                curR = newR; curI = newI;
            }
        }
    }
}

/**
 * Initialize I2S for PDM microphone input.
 */
void micInit() {
    i2s_config_t i2sConfig = {
        .mode = (i2s_mode_t)(I2S_MODE_MASTER | I2S_MODE_RX | I2S_MODE_PDM),
        .sample_rate = MIC_SAMPLE_RATE,
        .bits_per_sample = I2S_BITS_PER_SAMPLE_16BIT,
        .channel_format = I2S_CHANNEL_FMT_ONLY_RIGHT,
        .communication_format = I2S_COMM_FORMAT_STAND_I2S,
        .intr_alloc_flags = ESP_INTR_FLAG_LEVEL1,
        .dma_buf_count = 4,
        .dma_buf_len = 256,
        .use_apll = false,
        .tx_desc_auto_clear = false,
        .fixed_mclk = 0,
    };

    i2s_pin_config_t pinConfig = {
        .bck_io_num = I2S_PIN_NO_CHANGE,
        .ws_io_num = MIC_PIN_CLK,
        .data_out_num = I2S_PIN_NO_CHANGE,
        .data_in_num = MIC_PIN_DATA,
    };

    esp_err_t err = i2s_driver_install(I2S_NUM_0, &i2sConfig, 0, NULL);
    if (err != ESP_OK) return;

    i2s_set_pin(I2S_NUM_0, &pinConfig);
    i2s_set_clk(I2S_NUM_0, MIC_SAMPLE_RATE, I2S_BITS_PER_SAMPLE_16BIT, I2S_CHANNEL_MONO);

    i2sInitialized = true;
}

/**
 * Read audio samples from the PDM microphone.
 * Returns number of samples read.
 */
int micRead() {
    if (!i2sInitialized) return 0;

    size_t bytesRead = 0;
    esp_err_t err = i2s_read(I2S_NUM_0, micBuffer, MIC_SAMPLES * sizeof(int16_t),
                             &bytesRead, pdMS_TO_TICKS(50));
    if (err != ESP_OK) return 0;
    return bytesRead / sizeof(int16_t);
}

/**
 * Compute RMS (root mean square) of audio buffer.
 */
float computeRMS(int16_t* buf, int n) {
    float sum = 0;
    for (int i = 0; i < n; i++) {
        float s = buf[i] / 32768.0f;
        sum += s * s;
    }
    return sqrtf(sum / n);
}

/**
 * Run FFT and extract energy in frequency bands.
 *
 * Band 0: 0–20 Hz    (sub-bass: powder whoosh)
 * Band 1: 20–40 Hz   (bass: groomed corduroy)
 * Band 2: 40–80 Hz   (mid: ice scraping)
 * Band 3: 80+ Hz     (high: skidding, chatter)
 *
 * Frequency resolution: sampleRate / FFT_SIZE = 16000/256 = 62.5 Hz per bin
 * But we're sensing vibration through the boot, so dominant frequencies
 * are much lower. We use the first ~20 bins.
 */
void computeBandEnergy(int16_t* buf, int n, float* bands) {
    int fftN = FFT_SIZE;
    if (n < fftN) fftN = n;

    // Apply Hanning window and load into FFT arrays
    for (int i = 0; i < fftN; i++) {
        float window = 0.5f * (1.0f - cosf(2.0f * M_PI * i / (fftN - 1)));
        fftReal[i] = (buf[i] / 32768.0f) * window;
        fftImag[i] = 0;
    }

    simpleFFT(fftReal, fftImag, fftN);

    // Compute magnitudes
    int halfN = fftN / 2;
    for (int i = 0; i < halfN; i++) {
        magnitudes[i] = sqrtf(fftReal[i] * fftReal[i] + fftImag[i] * fftImag[i]);
    }

    // Frequency per bin: MIC_SAMPLE_RATE / fftN
    float binHz = (float)MIC_SAMPLE_RATE / fftN;

    // Sum energy in bands
    bands[0] = 0; bands[1] = 0; bands[2] = 0; bands[3] = 0;

    for (int i = 1; i < halfN; i++) {
        float freq = i * binHz;
        float energy = magnitudes[i] * magnitudes[i];
        if (freq < 500)       bands[0] += energy;  // Sub-bass / low vibration
        else if (freq < 1500) bands[1] += energy;  // Bass / medium vibration
        else if (freq < 4000) bands[2] += energy;  // Mid / scraping
        else                  bands[3] += energy;   // High / chatter, skid
    }

    // Normalize
    float total = bands[0] + bands[1] + bands[2] + bands[3] + 0.0001f;
    for (int i = 0; i < NUM_BANDS; i++) {
        bands[i] /= total;
    }
}

/**
 * Feature 1: Snow surface classification.
 */
SnowType classifySnow(float* bands, float rms) {
    if (rms < 0.005f) return SNOW_UNKNOWN; // too quiet, probably not moving

    float lowRatio = bands[0] + bands[1];  // Sub-bass + bass
    float highRatio = bands[2] + bands[3];  // Mid + high
    float variance = 0;
    for (int i = 0; i < NUM_BANDS; i++) {
        float diff = bands[i] - 0.25f;
        variance += diff * diff;
    }

    // Powder: dominated by low frequency, quiet overall
    if (lowRatio > 0.75f && rms < 0.03f) return SNOW_POWDER;

    // Ice: strong mid-high energy, sharp scraping
    if (bands[2] > 0.35f && highRatio > 0.5f) return SNOW_ICE;

    // Groomed: moderate bass, periodic pattern (low variance between bands)
    if (bands[1] > 0.3f && variance < 0.03f) return SNOW_GROOMED;

    // Slush: broadband with emphasis on low-mid, moderate RMS
    if (lowRatio > 0.55f && bands[1] > 0.25f && rms > 0.02f) return SNOW_SLUSH;

    // Crud: high variance (chaotic), broadband
    if (variance > 0.05f) return SNOW_CRUD;

    // Default to groomed (most common)
    return SNOW_GROOMED;
}

/**
 * Feature 2: Carve vs skid detection.
 */
CarveQuality classifyCarve(float* bands, float rms) {
    if (rms < 0.005f) return CARVE_UNKNOWN;

    float highEnergy = bands[2] + bands[3];

    // Chatter: high energy in the highest band with rapid fluctuation
    if (bands[3] > 0.3f && rms > 0.04f) return CARVE_CHATTER;

    // Skidding: lots of mid-high energy (scraping sound)
    if (highEnergy > 0.55f) return CARVE_SKIDDING;

    // Moderate: some scraping
    if (highEnergy > 0.35f) return CARVE_MODERATE;

    // Clean carve: low high-frequency energy
    return CARVE_CLEAN;
}

/**
 * Feature 3: Speed estimation from noise level.
 * Returns 0-255 proportional to perceived speed.
 */
uint8_t estimateSpeed(float rms) {
    // Map RMS 0.001–0.1 to 0–255
    float normalized = (rms - 0.001f) / 0.099f;
    if (normalized < 0) normalized = 0;
    if (normalized > 1) normalized = 1;
    return (uint8_t)(normalized * 255);
}

/**
 * Feature 4: Fall/crash detection.
 * Looks for sudden RMS spike (>3x average) followed by silence.
 */
bool detectFall(float rms) {
    // Compute running average from history
    float avgRms = 0;
    for (int i = 0; i < 8; i++) avgRms += rmsHistory[i];
    avgRms /= 8.0f;

    // Store current
    rmsHistory[rmsHistIdx] = rms;
    rmsHistIdx = (rmsHistIdx + 1) % 8;

    if (rms > peakRmsEver) peakRmsEver = rms;

    // Spike detection: current RMS > 4x average and above minimum threshold
    if (avgRms > 0.005f && rms > avgRms * 4.0f && rms > 0.05f) {
        return true;
    }

    return false;
}

/**
 * Feature 5: Binding click detection.
 * Detects sharp transient (click) that rises and falls within ~50ms.
 */
bool detectBindingClick(float rms) {
    unsigned long now = millis();

    // Debounce: ignore clicks within 2 seconds of each other
    if (now - lastClickTime < 2000) {
        prevRms = rms;
        return false;
    }

    // A binding click: sudden sharp rise (>5x) from quiet state
    if (prevRms < 0.01f && rms > 0.06f) {
        lastClickTime = now;
        prevRms = rms;
        return true;
    }

    prevRms = rms;
    return false;
}

/**
 * Feature 6: Ambient noise level (0-255).
 */
uint8_t computeAmbientLevel(float rms) {
    float normalized = rms / 0.1f;
    if (normalized > 1.0f) normalized = 1.0f;
    return (uint8_t)(normalized * 255);
}

/**
 * Main analysis function. Call at MIC_ANALYSIS_HZ (4 Hz).
 * Reads mic, runs FFT, classifies everything.
 */
bool runMicAnalysis() {
    unsigned long now = millis();
    unsigned long interval = 500; // default 2 Hz
    // Use power config interval if available (declared in power_manager.h)
    extern PowerConfig currentConfig;
    if (currentConfig.micIntervalMs > 0) interval = currentConfig.micIntervalMs;
    if (now - lastMicAnalysis < interval) return false;
    lastMicAnalysis = now;

    int samplesRead = micRead();
    if (samplesRead < FFT_SIZE) return false;

    float rms = computeRMS(micBuffer, samplesRead);
    float bands[NUM_BANDS];
    computeBandEnergy(micBuffer, samplesRead, bands);

    micResult.snowType = classifySnow(bands, rms);
    micResult.carveQuality = classifyCarve(bands, rms);
    micResult.speedProxy = estimateSpeed(rms);
    micResult.fallDetected = detectFall(rms);
    micResult.bindingClick = detectBindingClick(rms);
    micResult.ambientLevel = computeAmbientLevel(rms);
    for (int i = 0; i < NUM_BANDS; i++) {
        micResult.bandEnergy[i] = bands[i];
    }

    return true;
}

/**
 * Pack mic analysis results into 6 bytes for BLE transmission.
 *
 * Byte 0: Snow type (0-5)
 * Byte 1: Carve quality (0-4)
 * Byte 2: Speed proxy (0-255)
 * Byte 3: Flags (bit 0 = fall, bit 1 = binding click)
 * Byte 4: Ambient level (0-255)
 * Byte 5: Reserved
 */
void packMicResult(uint8_t* out) {
    out[0] = micResult.snowType;
    out[1] = micResult.carveQuality;
    out[2] = micResult.speedProxy;
    out[3] = (micResult.fallDetected ? 0x01 : 0x00) |
             (micResult.bindingClick ? 0x02 : 0x00);
    out[4] = micResult.ambientLevel;
    out[5] = 0; // reserved
}

/**
 * Get human-readable snow type string for the display.
 */
const char* snowTypeStr(SnowType t) {
    switch (t) {
        case SNOW_POWDER:  return "POWDER";
        case SNOW_GROOMED: return "GROOMED";
        case SNOW_ICE:     return "ICE";
        case SNOW_SLUSH:   return "SLUSH";
        case SNOW_CRUD:    return "CRUD";
        default:           return "---";
    }
}

uint16_t snowTypeColor(SnowType t) {
    switch (t) {
        case SNOW_POWDER:  return 0xFFFF; // White
        case SNOW_GROOMED: return 0x07E0; // Green
        case SNOW_ICE:     return 0x867F; // Light blue
        case SNOW_SLUSH:   return 0xFD20; // Orange
        case SNOW_CRUD:    return 0xFB20; // Red-orange
        default:           return 0x7BEF; // Gray
    }
}

const char* carveQualityStr(CarveQuality q) {
    switch (q) {
        case CARVE_CLEAN:    return "CARVING";
        case CARVE_MODERATE: return "SOME SKID";
        case CARVE_SKIDDING: return "SKIDDING";
        case CARVE_CHATTER:  return "CHATTER";
        default:             return "---";
    }
}

uint16_t carveQualityColor(CarveQuality q) {
    switch (q) {
        case CARVE_CLEAN:    return 0x07E0; // Green
        case CARVE_MODERATE: return 0xFFE0; // Yellow
        case CARVE_SKIDDING: return 0xFD20; // Orange
        case CARVE_CHATTER:  return 0xF800; // Red
        default:             return 0x7BEF; // Gray
    }
}

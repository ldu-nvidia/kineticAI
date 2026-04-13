/**
 * KineticAI 3-Segment Kinematic Chain Analysis
 *
 * Three IMUs create a biomechanical model of the lower leg:
 *
 *   Segment 1: SHELL  (MPU6886) — boot shell, rigidly attached to ski
 *   Segment 2: CUFF   (LSM9DS1) — boot cuff, hinges at ankle
 *   Segment 3: SHIN   (BNO055)  — shin, above boot, moves with lower leg
 *
 * Virtual joints derived from orientation differences:
 *   ANKLE joint = Cuff orientation − Shell orientation
 *   KNEE joint  = Shin orientation − Cuff orientation
 *
 * Metrics computed:
 *   1. Knee flexion angle (sagittal plane — bend/extend)
 *   2. Knee valgus/varus (frontal plane — ACL safety)
 *   3. Boot flex angle (ankle sagittal — forward lean)
 *   4. Ankle angulation (ankle frontal — edge technique)
 *   5. Kinetic chain timing (sequence of segment movements)
 *   6. Segment separation quality (independence of movement)
 *   7. Absorption profile (vibration propagation delay)
 *   8. Squat depth (minimum knee angle during turns)
 *   9. Rebound speed (knee extension rate at transition)
 */

#pragma once
#include <math.h>

// ── Tri-Segment Results ──

struct TriSegmentMetrics {
    // Segment orientations (degrees)
    float shellRoll, shellPitch;
    float cuffRoll, cuffPitch;
    float shinRoll, shinPitch, shinHeading;

    // ANKLE joint (cuff − shell)
    float ankleFlexion;         // Pitch diff: boot forward lean (positive = forward)
    float ankleAngulation;      // Roll diff: edge angle vs body lean

    // KNEE joint (shin − cuff)
    float kneeFlexion;          // Pitch diff: knee bend angle (positive = bent)
    float kneeValgus;           // Roll diff: inward collapse (positive = valgus = danger)

    // Safety
    bool aclWarning;            // Valgus > threshold during high-G turn
    float aclRiskScore;         // 0-100, higher = more risk

    // Kinetic chain
    float chainDelay;           // ms delay between shell and shin movement peaks
    uint8_t chainOrder;         // 0=bottom-up (expert), 1=top-down, 2=simultaneous (beginner)

    // Dynamics
    float sqautDepth;           // Min knee flexion during last turn (degrees)
    float reboundSpeed;         // Knee extension rate at transition (deg/s)
    float separationScore;      // 0-100: how independently segments move

    // Calibration
    bool allSensorsPresent;     // All three IMUs detected
    uint8_t bnoCalibration;     // BNO055 system calibration (0-3)

    bool valid;
};

static TriSegmentMetrics triMetrics = {};

// History buffers for kinetic chain timing
#define CHAIN_HIST_SIZE 20
static float shellGyroHist[CHAIN_HIST_SIZE] = {0};
static float cuffGyroHist[CHAIN_HIST_SIZE] = {0};
static float shinGyroHist[CHAIN_HIST_SIZE] = {0};
static uint8_t chainHistIdx = 0;

// Knee flexion tracking for squat depth and rebound
static float kneeFlexHistory[10] = {0};
static uint8_t kneeHistIdx = 0;
static float minKneeFlex = 180;
static float prevKneeFlex = 0;
static float maxExtensionRate = 0;

// Turn detection for per-turn reset
static bool inTurnForTri = false;

/**
 * Update 3-segment analysis with data from all three IMUs.
 *
 * Shell orientation comes from complementary filter on MPU6886 (in dual_imu.h).
 * Cuff orientation comes from complementary filter on LSM9DS1 (in dual_imu.h).
 * Shin orientation comes directly from BNO055 hardware fusion (no filtering needed).
 */
void updateTriSegment(
    float shellRoll, float shellPitch,
    float cuffRoll, float cuffPitch,
    float shinRoll, float shinPitch, float shinHeading,
    float shellGyroMag, float cuffGyroMag, float shinLinAccelMag,
    float currentGForce,
    uint8_t bnoCalSys,
    bool lsmPresent, bool bnoPresent,
    float dt
) {
    triMetrics.allSensorsPresent = lsmPresent && bnoPresent;
    triMetrics.bnoCalibration = bnoCalSys;

    // Store segment orientations
    triMetrics.shellRoll = shellRoll;
    triMetrics.shellPitch = shellPitch;
    triMetrics.cuffRoll = cuffRoll;
    triMetrics.cuffPitch = cuffPitch;
    triMetrics.shinRoll = shinRoll;
    triMetrics.shinPitch = shinPitch;
    triMetrics.shinHeading = shinHeading;

    // ── ANKLE JOINT (cuff − shell) ──
    triMetrics.ankleFlexion = cuffPitch - shellPitch;
    triMetrics.ankleAngulation = fabsf(shellRoll) - fabsf(cuffRoll);

    // ── KNEE JOINT (shin − cuff) ──
    // Flexion: how much the knee is bent
    // When standing straight: shin and cuff are parallel → diff ≈ 0
    // When crouching: shin tilts back relative to cuff → positive flexion
    triMetrics.kneeFlexion = cuffPitch - shinPitch;
    if (triMetrics.kneeFlexion < 0) triMetrics.kneeFlexion = 0; // Knee doesn't hyperextend

    // Valgus: lateral knee collapse
    // If shin rolls inward MORE than cuff → knee is collapsing inward
    triMetrics.kneeValgus = shinRoll - cuffRoll;

    // ── ACL SAFETY ──
    // Danger: high valgus angle during high G-force (loaded turn)
    float valgusAbs = fabsf(triMetrics.kneeValgus);
    triMetrics.aclRiskScore = 0;

    if (valgusAbs > 5.0f) {
        // Base risk from valgus angle (0-50 points)
        triMetrics.aclRiskScore = fminf(50.0f, valgusAbs * 3.3f);

        // Multiplied by G-force load (0-50 points)
        if (currentGForce > 1.0f) {
            triMetrics.aclRiskScore += fminf(50.0f, (currentGForce - 1.0f) * 25.0f);
        }
    }

    // Warning threshold: risk > 60 = real danger
    triMetrics.aclWarning = (triMetrics.aclRiskScore > 60.0f);

    // ── SQUAT DEPTH & REBOUND ──
    kneeFlexHistory[kneeHistIdx] = triMetrics.kneeFlexion;
    kneeHistIdx = (kneeHistIdx + 1) % 10;

    if (triMetrics.kneeFlexion < minKneeFlex) {
        minKneeFlex = triMetrics.kneeFlexion;
    }
    triMetrics.sqautDepth = minKneeFlex;

    // Rebound speed: rate of knee extension (negative change in flexion)
    float extensionRate = (prevKneeFlex - triMetrics.kneeFlexion) / dt;
    if (extensionRate > maxExtensionRate) maxExtensionRate = extensionRate;
    triMetrics.reboundSpeed = maxExtensionRate;
    prevKneeFlex = triMetrics.kneeFlexion;

    // ── KINETIC CHAIN TIMING ──
    // Store gyro magnitudes for all three segments
    shellGyroHist[chainHistIdx] = shellGyroMag;
    cuffGyroHist[chainHistIdx] = cuffGyroMag;
    shinGyroHist[chainHistIdx] = shinLinAccelMag;
    chainHistIdx = (chainHistIdx + 1) % CHAIN_HIST_SIZE;

    // Find peak timing for each segment in history
    int shellPeakIdx = 0, cuffPeakIdx = 0, shinPeakIdx = 0;
    float shellPeak = 0, cuffPeak = 0, shinPeak = 0;
    for (int i = 0; i < CHAIN_HIST_SIZE; i++) {
        if (shellGyroHist[i] > shellPeak) { shellPeak = shellGyroHist[i]; shellPeakIdx = i; }
        if (cuffGyroHist[i] > cuffPeak) { cuffPeak = cuffGyroHist[i]; cuffPeakIdx = i; }
        if (shinGyroHist[i] > shinPeak) { shinPeak = shinGyroHist[i]; shinPeakIdx = i; }
    }

    // Chain order: expert = shell first (bottom-up), beginner = simultaneous
    int shellFirst = (CHAIN_HIST_SIZE + chainHistIdx - shellPeakIdx) % CHAIN_HIST_SIZE;
    int cuffSecond = (CHAIN_HIST_SIZE + chainHistIdx - cuffPeakIdx) % CHAIN_HIST_SIZE;
    int shinThird = (CHAIN_HIST_SIZE + chainHistIdx - shinPeakIdx) % CHAIN_HIST_SIZE;

    if (shellFirst > cuffSecond && cuffSecond > shinThird) {
        triMetrics.chainOrder = 0; // Bottom-up (expert)
    } else if (shinThird > cuffSecond && cuffSecond > shellFirst) {
        triMetrics.chainOrder = 1; // Top-down
    } else {
        triMetrics.chainOrder = 2; // Simultaneous (beginner)
    }

    float chainSpread = abs(shellFirst - shinThird) * (dt * 1000.0f);
    triMetrics.chainDelay = chainSpread;

    // ── SEPARATION SCORE ──
    // How differently each segment moves (higher = more independent = expert)
    float rollDiffs = fabsf(shellRoll - cuffRoll) + fabsf(cuffRoll - shinRoll);
    float pitchDiffs = fabsf(shellPitch - cuffPitch) + fabsf(cuffPitch - shinPitch);
    triMetrics.separationScore = fminf(100.0f, (rollDiffs + pitchDiffs) * 2.0f);

    triMetrics.valid = true;
}

/**
 * Reset per-turn tracking (call when new turn detected).
 */
void triSegmentNewTurn() {
    minKneeFlex = 180;
    maxExtensionRate = 0;
}

/**
 * Pack tri-segment metrics into 12 bytes for BLE.
 *
 * Byte 0:  Knee flexion (uint8, degrees, 0-180)
 * Byte 1:  Knee valgus (int8, degrees, ±127)
 * Byte 2:  Ankle flexion / boot flex (int8, degrees, ±127)
 * Byte 3:  Ankle angulation (int8, degrees, ±127)
 * Byte 4:  ACL risk score (uint8, 0-100)
 * Byte 5:  Squat depth (uint8, degrees, 0-180)
 * Byte 6:  Rebound speed (uint8, deg/s / 4, 0-255 = 0-1020 deg/s)
 * Byte 7:  Chain order (uint8, 0=bottom-up, 1=top-down, 2=simultaneous)
 * Byte 8:  Separation score (uint8, 0-100)
 * Byte 9:  Shin heading (uint8, degrees/2, 0-179 = 0-358°)
 * Byte 10: BNO calibration (uint8, 0-3)
 * Byte 11: Flags (bit0=aclWarning, bit1=allSensorsPresent, bit2=bnoPresent)
 */
void packTriSegmentMetrics(uint8_t* out) {
    out[0] = (uint8_t)constrain(triMetrics.kneeFlexion, 0, 180);
    out[1] = (int8_t)constrain(triMetrics.kneeValgus, -127, 127);
    out[2] = (int8_t)constrain(triMetrics.ankleFlexion, -127, 127);
    out[3] = (int8_t)constrain(triMetrics.ankleAngulation, -127, 127);
    out[4] = (uint8_t)constrain(triMetrics.aclRiskScore, 0, 100);
    out[5] = (uint8_t)constrain(triMetrics.sqautDepth, 0, 180);
    out[6] = (uint8_t)constrain(triMetrics.reboundSpeed / 4.0f, 0, 255);
    out[7] = triMetrics.chainOrder;
    out[8] = (uint8_t)constrain(triMetrics.separationScore, 0, 100);
    out[9] = (uint8_t)(((int)triMetrics.shinHeading / 2) % 180);
    out[10] = triMetrics.bnoCalibration;
    out[11] = (triMetrics.aclWarning ? 0x01 : 0x00) |
              (triMetrics.allSensorsPresent ? 0x02 : 0x00) |
              (bno055Present ? 0x04 : 0x00);
}

const char* chainOrderStr(uint8_t order) {
    switch (order) {
        case 0: return "BOTTOM-UP";  // Expert: feet initiate
        case 1: return "TOP-DOWN";   // Upper body leads
        case 2: return "SIMULTANEOUS"; // Beginner: rigid
        default: return "?";
    }
}

/**
 * Dual IMU Analysis — Boot Biomechanics from Two-Segment Kinematics
 *
 * By mounting two IMUs on the same boot at different locations:
 *   - Shell IMU (MPU6886, built-in): mounted on boot shell (lower, fixed to ski)
 *   - Cuff IMU (LSM9DS1, external): mounted on boot cuff (upper, hinges with shin)
 *
 * The angular DIFFERENCE between them reveals:
 *   - Boot flex angle (pitch difference)
 *   - Ankle angulation (roll difference)
 *   - Ankle steering (yaw difference)
 *   - Vibration isolation (frequency separation)
 *   - Absorption quality (independent motion during bumps)
 *
 * This is a 2-segment kinematic chain — the same technique used in
 * biomechanics research labs with optical motion capture, but achieved
 * with two $10 IMU chips.
 */

#pragma once
#include <math.h>

// ── Dual IMU Results ──

struct DualImuMetrics {
    // Orientation of each segment (degrees)
    float shellRoll, shellPitch, shellYaw;
    float cuffRoll, cuffPitch, cuffYaw;

    // Derived joint angles (cuff - shell, degrees)
    float bootFlexAngle;        // Pitch difference: forward lean of cuff relative to shell
    float angulationAngle;      // Roll difference: ankle tilt independent of ski tilt
    float ankleSteeringAngle;   // Yaw difference: foot rotation vs leg rotation

    // Vibration analysis
    float shellVibration;       // High-freq accel magnitude on shell (ski vibration)
    float cuffVibration;        // High-freq accel magnitude on cuff (body vibration)
    float vibrationDamping;     // Ratio: how much vibration the boot absorbs (0-1)

    // Absorption quality
    float absorptionScore;      // 0-100: how independently the segments move over bumps

    // Knee safety
    float lateralStressDiff;    // Lateral force mismatch between shell and cuff
    bool kneeStressWarning;     // True if lateral stress difference is dangerous

    // Magnetometer heading (from LSM9DS1 only)
    float heading;              // Compass heading in degrees (0-360)

    // Distance sensor
    uint8_t distanceMm;         // Distance to snow surface
    bool airborne;              // In the air (jump detected)

    bool valid;
};

static DualImuMetrics dualMetrics = {};

// Orientation state (simple complementary filter per segment)
static float shellRollF = 0, shellPitchF = 0;
static float cuffRollF = 0, cuffPitchF = 0, cuffYawF = 0;
static const float COMP_ALPHA = 0.98f; // Complementary filter weight for gyro

// Vibration high-pass filter state
static float shellAccelPrev[3] = {0, 0, 0};
static float cuffAccelPrev[3] = {0, 0, 0};

// Absorption tracking
static float shellPitchHistory[10] = {0};
static float cuffPitchHistory[10] = {0};
static uint8_t absHistIdx = 0;

/**
 * Compute orientation from accel + gyro using complementary filter.
 * Returns roll and pitch in degrees.
 */
void complementaryFilter(
    float ax, float ay, float az,
    float gx, float gy, float gz,
    float dt,
    float& roll, float& pitch
) {
    // Accel-derived angles
    float accelRoll = atan2f(ay, sqrtf(ax * ax + az * az)) * 57.2958f;
    float accelPitch = atan2f(-ax, sqrtf(ay * ay + az * az)) * 57.2958f;

    // Gyro integration (degrees)
    float gyroRollDelta = gx * 57.2958f * dt;
    float gyroPitchDelta = gy * 57.2958f * dt;

    // Complementary filter: trust gyro short-term, accel long-term
    roll = COMP_ALPHA * (roll + gyroRollDelta) + (1.0f - COMP_ALPHA) * accelRoll;
    pitch = COMP_ALPHA * (pitch + gyroPitchDelta) + (1.0f - COMP_ALPHA) * accelPitch;
}

/**
 * Compute heading from magnetometer (LSM9DS1).
 * Tilt-compensated using accel-derived roll/pitch.
 */
float computeHeading(float mx, float my, float mz, float roll, float pitch) {
    float rollRad = roll * 0.01745329f;
    float pitchRad = pitch * 0.01745329f;

    float cosR = cosf(rollRad), sinR = sinf(rollRad);
    float cosP = cosf(pitchRad), sinP = sinf(pitchRad);

    // Tilt compensation
    float xH = mx * cosP + my * sinR * sinP + mz * cosR * sinP;
    float yH = my * cosR - mz * sinR;

    float heading = atan2f(-yH, xH) * 57.2958f;
    if (heading < 0) heading += 360.0f;
    return heading;
}

/**
 * High-pass filter for vibration extraction.
 * Returns the high-frequency component magnitude.
 */
float extractVibration(float ax, float ay, float az, float* prev) {
    float hpX = ax - prev[0];
    float hpY = ay - prev[1];
    float hpZ = az - prev[2];
    prev[0] = prev[0] * 0.95f + ax * 0.05f; // Slow-moving average
    prev[1] = prev[1] * 0.95f + ay * 0.05f;
    prev[2] = prev[2] * 0.95f + az * 0.05f;
    return sqrtf(hpX * hpX + hpY * hpY + hpZ * hpZ);
}

/**
 * Compute absorption score from how independently the two segments move.
 * High score = cuff and shell move independently (good shock absorption).
 * Low score = they move together rigidly (stiff, poor absorption).
 */
float computeAbsorption() {
    float diffSum = 0;
    for (int i = 0; i < 10; i++) {
        float diff = fabsf(shellPitchHistory[i] - cuffPitchHistory[i]);
        diffSum += diff;
    }
    float avgDiff = diffSum / 10.0f;

    // Map 0-10° average difference to 0-100 score
    return fminf(100.0f, avgDiff * 10.0f);
}

/**
 * Main dual IMU analysis. Call every IMU cycle (~50 Hz).
 *
 * @param shellAx..shellGz  Shell IMU data (MPU6886, in m/s² and rad/s)
 * @param cuffAx..cuffGz    Cuff IMU data (LSM9DS1, in m/s² and rad/s)
 * @param mx,my,mz          Magnetometer data (LSM9DS1, in µT)
 * @param distMm            VL6180X distance reading (mm)
 * @param isAirborne        VL6180X airborne flag
 * @param dt                Time step in seconds
 */
void updateDualImu(
    float shellAx, float shellAy, float shellAz,
    float shellGx, float shellGy, float shellGz,
    float cuffAx, float cuffAy, float cuffAz,
    float cuffGx, float cuffGy, float cuffGz,
    float mx, float my, float mz,
    uint8_t distMm, bool isAirborne,
    float dt
) {
    // ── Orientation estimation per segment ──
    complementaryFilter(shellAx, shellAy, shellAz, shellGx, shellGy, shellGz,
                        dt, shellRollF, shellPitchF);
    complementaryFilter(cuffAx, cuffAy, cuffAz, cuffGx, cuffGy, cuffGz,
                        dt, cuffRollF, cuffPitchF);

    dualMetrics.shellRoll = shellRollF;
    dualMetrics.shellPitch = shellPitchF;
    dualMetrics.cuffRoll = cuffRollF;
    dualMetrics.cuffPitch = cuffPitchF;

    // ── Joint angles (cuff relative to shell) ──
    // Boot flex: positive = cuff leaning forward relative to shell (good forward pressure)
    dualMetrics.bootFlexAngle = cuffPitchF - shellPitchF;

    // Angulation: roll difference = ankle tilt independent of ski tilt
    dualMetrics.angulationAngle = fabsf(cuffRollF) - fabsf(shellRollF);

    // Ankle steering: yaw difference (requires heading from magnetometer)
    float cuffYawRate = cuffGz * 57.2958f * dt;
    float shellYawRate = shellGz * 57.2958f * dt;
    dualMetrics.ankleSteeringAngle += (cuffYawRate - shellYawRate) * dt;
    // Decay slowly to prevent drift
    dualMetrics.ankleSteeringAngle *= 0.995f;

    // ── Heading from magnetometer ──
    dualMetrics.heading = computeHeading(mx, my, mz, cuffRollF, cuffPitchF);

    // ── Vibration analysis ──
    dualMetrics.shellVibration = extractVibration(shellAx, shellAy, shellAz, shellAccelPrev);
    dualMetrics.cuffVibration = extractVibration(cuffAx, cuffAy, cuffAz, cuffAccelPrev);

    // Damping ratio: 1.0 = cuff feels no vibration (perfect damping)
    if (dualMetrics.shellVibration > 0.1f) {
        dualMetrics.vibrationDamping = 1.0f - (dualMetrics.cuffVibration / dualMetrics.shellVibration);
        if (dualMetrics.vibrationDamping < 0) dualMetrics.vibrationDamping = 0;
    } else {
        dualMetrics.vibrationDamping = 1.0f;
    }

    // ── Absorption tracking ──
    shellPitchHistory[absHistIdx] = shellPitchF;
    cuffPitchHistory[absHistIdx] = cuffPitchF;
    absHistIdx = (absHistIdx + 1) % 10;
    dualMetrics.absorptionScore = computeAbsorption();

    // ── Knee safety: lateral stress mismatch ──
    // If shell has high lateral accel but cuff doesn't (or vice versa),
    // the knee is absorbing the difference — potential ACL stress
    float shellLateral = fabsf(shellAx); // Lateral accel on shell
    float cuffLateral = fabsf(cuffAx);   // Lateral accel on cuff
    dualMetrics.lateralStressDiff = fabsf(shellLateral - cuffLateral);
    dualMetrics.kneeStressWarning = (dualMetrics.lateralStressDiff > 30.0f); // >3G difference

    // ── Distance sensor ──
    dualMetrics.distanceMm = distMm;
    dualMetrics.airborne = isAirborne;

    dualMetrics.valid = true;
}

/**
 * Pack dual IMU metrics into 10 bytes for BLE transmission.
 *
 * Byte 0: Boot flex angle (int8, degrees, ±127°)
 * Byte 1: Angulation angle (int8, degrees, ±127°)
 * Byte 2: Ankle steering angle (int8, degrees, ±127°)
 * Byte 3: Shell vibration (uint8, 0-255, scaled)
 * Byte 4: Vibration damping (uint8, 0-100%)
 * Byte 5: Absorption score (uint8, 0-100)
 * Byte 6: Heading (uint8, degrees/2, 0-179 = 0-358°)
 * Byte 7: Distance mm (uint8, 0-255)
 * Byte 8: Flags (bit0=airborne, bit1=kneeStressWarning, bit2=lsm9ds1Present, bit3=vl6180xPresent)
 * Byte 9: Lateral stress diff (uint8, scaled)
 */
void packDualImuMetrics(uint8_t* out) {
    out[0] = (int8_t)constrain(dualMetrics.bootFlexAngle, -127, 127);
    out[1] = (int8_t)constrain(dualMetrics.angulationAngle, -127, 127);
    out[2] = (int8_t)constrain(dualMetrics.ankleSteeringAngle, -127, 127);
    out[3] = (uint8_t)constrain(dualMetrics.shellVibration * 25.0f, 0, 255);
    out[4] = (uint8_t)constrain(dualMetrics.vibrationDamping * 100.0f, 0, 100);
    out[5] = (uint8_t)constrain(dualMetrics.absorptionScore, 0, 100);
    out[6] = (uint8_t)(((int)dualMetrics.heading / 2) % 180);
    out[7] = dualMetrics.distanceMm;
    out[8] = (dualMetrics.airborne ? 0x01 : 0x00) |
             (dualMetrics.kneeStressWarning ? 0x02 : 0x00) |
             (lsm9ds1Present ? 0x04 : 0x00) |
             (vl6180xPresent ? 0x08 : 0x00);
    out[9] = (uint8_t)constrain(dualMetrics.lateralStressDiff, 0, 255);
}

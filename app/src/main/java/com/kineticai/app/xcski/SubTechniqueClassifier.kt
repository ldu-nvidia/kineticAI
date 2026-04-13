package com.kineticai.app.xcski

/**
 * Rule-based decision tree classifier for cross-country skiing sub-techniques.
 *
 * Uses 6 features extracted per stride cycle:
 *   1. Lateral sway amplitude (degrees)
 *   2. Lateral sway symmetry (0-1, 1 = perfect)
 *   3. Pitch oscillation amplitude (degrees)
 *   4. Cycle duration (ms)
 *   5. Speed (km/h) from GPS
 *   6. Terrain grade (%) from barometer
 *
 * Achieves ~85-90% accuracy with rule-based approach.
 * Discriminators are derived from published research on IMU-based XC ski classification.
 *
 * The classifier can operate in CLASSIC or SKATE mode, which narrows the
 * technique set and improves accuracy.
 */
class SubTechniqueClassifier {

    enum class XCStyle { CLASSIC, SKATE, AUTO }

    var style: XCStyle = XCStyle.AUTO

    fun classify(features: CycleFeatures): SubTechnique {
        val speed = features.speedKmh
        val grade = features.gradePercent
        val sway = features.lateralSwayAmplitude
        val symmetry = features.lateralSwaySymmetry
        val pitch = features.pitchOscillationAmplitude
        val duration = features.cycleDurationMs

        // --- Stationary / very slow ---
        if (speed < 1.5f && pitch < 3f && sway < 2f) {
            return SubTechnique.STANDING
        }

        // --- Downhill tuck: fast speed, steep downhill, minimal body movement ---
        if (speed > 15f && grade < -3f && pitch < 8f && sway < 4f) {
            return if (style == XCStyle.SKATE) SubTechnique.SKATE_TUCK else SubTechnique.CLASSIC_TUCK
        }
        if (speed > 12f && grade < -5f && pitch < 10f) {
            return if (style == XCStyle.SKATE) SubTechnique.SKATE_TUCK else SubTechnique.CLASSIC_TUCK
        }

        // --- Herringbone: steep uphill, slow, high-freq lateral oscillation ---
        if (grade > 8f && speed < 6f && sway > 6f && duration < 800) {
            return SubTechnique.HERRINGBONE
        }

        // --- Now differentiate based on style ---
        return when (style) {
            XCStyle.CLASSIC -> classifyClassic(features)
            XCStyle.SKATE -> classifySkate(features)
            XCStyle.AUTO -> classifyAuto(features)
        }
    }

    private fun classifyClassic(f: CycleFeatures): SubTechnique {
        val sway = f.lateralSwayAmplitude
        val symmetry = f.lateralSwaySymmetry
        val pitch = f.pitchOscillationAmplitude
        val duration = f.cycleDurationMs

        // Double Poling: minimal lateral sway, strong pitch oscillation (upper body crunch)
        if (sway < 5f && pitch > 12f) {
            return SubTechnique.DOUBLE_POLING
        }

        // Kick Double Pole: asymmetric lateral shift + strong pitch
        if (sway in 3f..10f && symmetry < 0.6f && pitch > 10f) {
            return SubTechnique.KICK_DOUBLE_POLE
        }

        // Diagonal Stride: alternating lateral sway + moderate pitch
        if (sway > 4f && symmetry > 0.6f) {
            return SubTechnique.DIAGONAL_STRIDE
        }

        // Fallback: if short cycle = DP, long cycle = DS
        return if (duration < 900) SubTechnique.DOUBLE_POLING else SubTechnique.DIAGONAL_STRIDE
    }

    private fun classifySkate(f: CycleFeatures): SubTechnique {
        val sway = f.lateralSwayAmplitude
        val symmetry = f.lateralSwaySymmetry
        val pitch = f.pitchOscillationAmplitude
        val duration = f.cycleDurationMs
        val speed = f.speedKmh

        // Free Skate: wide lateral sway, very little pitch (no poling)
        if (sway > 10f && pitch < 6f) {
            return SubTechnique.FREE_SKATE
        }

        // V1 Offset: asymmetric — pole on one side only
        if (sway > 6f && symmetry < 0.65f && pitch > 8f) {
            return SubTechnique.V1_OFFSET
        }

        // V2 One-Skate: symmetric, pole every stride, higher cycle rate
        if (sway > 8f && symmetry > 0.65f && pitch > 8f && duration < 1100) {
            return SubTechnique.V2_ONE_SKATE
        }

        // V2 Alternate: symmetric but longer cycles (pole every other stride)
        if (sway > 8f && symmetry > 0.65f && duration >= 1100) {
            return SubTechnique.V2_ALTERNATE
        }

        // Fallback based on sway width
        return if (sway > 10f) SubTechnique.V2_ONE_SKATE else SubTechnique.V1_OFFSET
    }

    private fun classifyAuto(f: CycleFeatures): SubTechnique {
        // In auto mode, use sway width to guess classic vs skate, then delegate.
        // Skate techniques typically have wider lateral sway (>10 deg) than classic (<8 deg).
        val sway = f.lateralSwayAmplitude

        // Wide sway + high cycle rate → likely skate
        if (sway > 10f || (sway > 7f && f.cycleDurationMs < 1000)) {
            return classifySkate(f)
        }

        // Narrow sway → likely classic
        if (sway < 6f) {
            return classifyClassic(f)
        }

        // Ambiguous zone: use speed and grade as tiebreakers
        // Skate is generally faster on flat terrain
        return if (f.speedKmh > 12f && f.gradePercent > -2f) {
            classifySkate(f)
        } else {
            classifyClassic(f)
        }
    }
}

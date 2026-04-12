package com.mycarv.app.analysis

/**
 * Complete per-turn data including all 12 technique metrics and raw time-series
 * for post-run analysis.
 */
data class DetectedTurn(
    val direction: TurnDirection,
    val startTime: Long,
    val endTime: Long,
    val peakGyroZ: Float,

    // ── EDGING ──
    val edgeAngle: Float,              // peak absolute roll (degrees)
    val earlyEdgingScore: Float,       // 0–100: how early peak edge is reached
    val progressiveEdgeScore: Float,   // 0–100: sustained edge in 10–80% of turn
    // edgeSimilarity computed across turns, not per-turn

    // ── ROTARY ──
    val turnShapeScore: Float,         // 0–100: sinusoidal (C) vs step (Z) correlation

    // ── PRESSURE ──
    val gForce: Float,                 // peak lateral G
    val weightReleaseScore: Float,     // 0–100: dynamic unweighting at transition
    val pressureSmoothnessScore: Float, // 0–100: inverse of lateral-G variance

    // ── BALANCE ──
    val earlyForwardScore: Float,      // 0–100: forward pitch projection at initiation
    val centeredBalanceScore: Float,    // 0–100: pitch stability during mid-turn

    // ── Raw time-series for post-run charts ──
    val rollSamples: List<Float> = emptyList(),
    val pitchSamples: List<Float> = emptyList(),
    val verticalGSamples: List<Float> = emptyList(),
    val lateralGSamples: List<Float> = emptyList(),
) {
    val durationMs: Long get() = endTime - startTime
}

enum class TurnDirection { LEFT, RIGHT, NONE }

/**
 * Aggregated metrics across an entire run. Structured in the 4 fundamental
 * skiing skill categories (PSIA/CSIA framework).
 */
data class SkiMetrics(
    // ── SPEED & DISTANCE ──
    val currentSpeed: Float = 0f,
    val maxSpeed: Float = 0f,
    val averageSpeed: Float = 0f,
    val currentSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val totalDistance: Float = 0f,
    val altitudeDrop: Float = 0f,
    val currentAltitude: Double = 0.0,
    val startAltitude: Double = 0.0,

    // ── TURN COUNTS ──
    val turnCount: Int = 0,
    val leftTurnCount: Int = 0,
    val rightTurnCount: Int = 0,

    // ── BALANCE SCORES (0–100) ──
    val earlyForwardMovement: Float = 0f,
    val centeredBalance: Float = 0f,

    // ── EDGING SCORES (0–100) ──
    val edgeAngle: Float = 0f,         // average peak edge angle (degrees, not score)
    val edgeAngleScore: Float = 0f,    // normalized 0–100
    val earlyEdging: Float = 0f,
    val edgeSimilarity: Float = 0f,
    val progressiveEdgeBuild: Float = 0f,

    // ── ROTARY SCORES (0–100) ──
    val parallelSkis: Float = 0f,
    val turnShape: Float = 0f,

    // ── PRESSURE SCORES (0–100) ──
    val outsideSkiPressure: Float = 0f,
    val pressureSmoothness: Float = 0f,
    val earlyWeightTransfer: Float = 0f,
    val transitionWeightRelease: Float = 0f,
    val gForce: Float = 0f,           // average peak lateral G
    val maxGForce: Float = 0f,

    // ── COMPOSITE ──
    val skiIQ: Int = 0,                // 0–200 composite score
    val balanceScore: Float = 0f,      // category average 0–100
    val edgingScore: Float = 0f,
    val rotaryScore: Float = 0f,
    val pressureScore: Float = 0f,

    // ── ADVANCED (from dual/tri IMU + mic) ──
    val bootFlexAngle: Float = 0f,          // degrees, forward lean (dual IMU)
    val ankleAngulation: Float = 0f,        // degrees, ankle tilt vs ski tilt
    val kneeFlexion: Float = 0f,            // degrees, knee bend (tri-segment)
    val kneeValgusMax: Float = 0f,          // max knee inward collapse
    val aclRiskMax: Float = 0f,             // max ACL risk score seen
    val kinChainBottomUpPct: Float = 0f,    // % of turns with expert bottom-up initiation
    val separationAvg: Float = 0f,          // average segment separation score
    val carveQualityScore: Float = 0f,      // 0-100 from mic (clean=100, skid=0)
    val snowType: String = "Unknown",       // current snow type label
    val jumpCount: Int = 0,
    val totalAirtimeMs: Long = 0,

    // ── ASYMMETRY (left vs right per-metric comparison) ──
    val leftEdgeAngleAvg: Float = 0f,
    val rightEdgeAngleAvg: Float = 0f,
    val leftGForceAvg: Float = 0f,
    val rightGForceAvg: Float = 0f,
    val leftEarlyEdgingAvg: Float = 0f,
    val rightEarlyEdgingAvg: Float = 0f,
    val weakerSide: String = "",            // "LEFT" or "RIGHT" or ""
    val asymmetryScore: Float = 100f,       // 100=symmetric, 0=very asymmetric
    val weakerSideMetric: String = "",      // which metric differs most

    // ── FATIGUE ──
    val fatigueIndicator: Float = 0f,       // 0=fresh, 100=exhausted (Ski:IQ decline rate)
    val skiIQTrend: Float = 0f,             // positive=improving, negative=declining
    val earlyRunSkiIQ: Float = 0f,          // avg Ski:IQ from first 10 turns
    val currentWindowSkiIQ: Float = 0f,     // avg Ski:IQ from last 10 turns

    // ── TURN PHASE SCORES (per-phase averages, 0-100) ──
    val phaseInitScore: Float = 0f,         // early edging + weight transfer in 0-25%
    val phaseSteerInScore: Float = 0f,      // progressive edge + knee deepening in 25-50%
    val phaseApexScore: Float = 0f,         // peak edge + peak G in 50%
    val phaseSteerOutScore: Float = 0f,     // edge sustain + turn closure in 50-85%
    val phaseTransitionScore: Float = 0f,   // weight release + rebound in 85-100%
    val weakestPhase: String = "",          // which phase needs most work

    // ── ADAPTIVE SCORING ──
    val personalBestSkiIQ: Int = 0,         // highest Ski:IQ ever achieved
    val personalBaselineSkiIQ: Float = 0f,  // baseline from first 50 turns
    val improvementPct: Float = 0f,         // % improvement over personal baseline

    // ── STATE ──
    val runDurationMs: Long = 0,
    val isMoving: Boolean = false,
    val currentGForce: Float = 1f,
    val currentEdgeAngle: Float = 0f,
    val currentRoll: Float = 0f,
    val currentPitch: Float = 0f,
) {
    val turnSymmetry: Float
        get() {
            val total = leftTurnCount + rightTurnCount
            return if (total == 0) 1f
            else 1f - kotlin.math.abs(leftTurnCount - rightTurnCount).toFloat() / total
        }

    val turnSymmetryPercent: Int get() = (turnSymmetry * 100).toInt()

    val runDurationFormatted: String
        get() {
            val totalSec = runDurationMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            return "%d:%02d".format(min, sec)
        }
}

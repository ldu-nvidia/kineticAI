package com.mycarv.app.snowboard

/**
 * Snowboard-specific metrics derived from the same sensor hardware as skiing.
 *
 * Key differences from skiing:
 *   - Turns are toeside/heelside (not left/right)
 *   - One board with two edges (not two independent skis)
 *   - Stance is sideways — roll = edge angle, yaw = board direction
 *   - Tricks: spins, presses, ollies, grabs are measurable
 *   - ACL risk is rotational (not valgus)
 */

enum class BoardEdge { TOESIDE, HEELSIDE, FLAT, UNKNOWN }

enum class TrickType {
    NONE,
    OLLIE,          // Pop from tail
    NOLLIE,         // Pop from nose
    NOSE_PRESS,     // Weight on nose, tail lifted
    TAIL_PRESS,     // Weight on tail, nose lifted
    BUTTER,         // Ground spin on nose or tail
    SPIN_180,       // Half rotation
    SPIN_360,       // Full rotation
    SPIN_540,       // 1.5 rotations
    SPIN_720,       // 2 full rotations
    SHIFTY,         // Board rotates but body doesn't (counter-rotation in air)
    METHOD,         // Back-hand grab with board tweaked
}

enum class Stance { REGULAR, GOOFY, SWITCH, UNKNOWN }

data class DetectedTrick(
    val type: TrickType,
    val startTime: Long,
    val endTime: Long,
    val airtimeMs: Long,
    val rotationDegrees: Float,
    val maxHeight: Float,           // from VL6180X if available
    val peakGOnLanding: Float,
    val style: Int,                 // 0-100 (smooth landing, clean rotation)
)

data class SnowboardTurn(
    val edge: BoardEdge,
    val startTime: Long,
    val endTime: Long,
    val peakEdgeAngle: Float,
    val earlyEdgingScore: Float,
    val progressiveEdgeScore: Float,
    val turnShapeScore: Float,
    val gForce: Float,
    val weightReleaseScore: Float,
    val carveQualityScore: Float,
    val pressureDistribution: Float, // front-back balance: 0=all tail, 50=center, 100=all nose
)

data class SnowboardMetrics(
    // ── SPEED & DISTANCE ──
    val currentSpeed: Float = 0f,
    val maxSpeed: Float = 0f,
    val currentSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val totalDistance: Float = 0f,
    val altitudeDrop: Float = 0f,

    // ── TURNS ──
    val turnCount: Int = 0,
    val toesideTurnCount: Int = 0,
    val heelsideTurnCount: Int = 0,
    val currentEdge: BoardEdge = BoardEdge.FLAT,

    // ── EDGE SCORES (0-100) ──
    val edgeAngle: Float = 0f,
    val edgeAngleScore: Float = 0f,
    val earlyEdging: Float = 0f,
    val edgeSymmetry: Float = 0f,       // toeside vs heelside similarity
    val progressiveEdgeBuild: Float = 0f,
    val toesideEdgeAvg: Float = 0f,
    val heelsideEdgeAvg: Float = 0f,

    // ── BOARD CONTROL SCORES ──
    val turnShape: Float = 0f,
    val pressureFrontBack: Float = 50f, // 0=tail heavy, 50=centered, 100=nose heavy
    val boardTwist: Float = 0f,         // torsional flex (if 2 boot sensors)
    val carveQuality: Float = 0f,

    // ── PRESSURE ──
    val gForce: Float = 0f,
    val maxGForce: Float = 0f,
    val weightRelease: Float = 0f,

    // ── TRICKS ──
    val trickCount: Int = 0,
    val totalAirtimeMs: Long = 0,
    val bestTrickType: TrickType = TrickType.NONE,
    val maxSpinDegrees: Float = 0f,
    val trickStyleAvg: Float = 0f,
    val isSwitch: Boolean = false,      // currently riding switch
    val switchPercent: Float = 0f,      // % of run spent in switch

    // ── BALANCE ──
    val centeredBalance: Float = 0f,
    val bootFlexAngle: Float = 0f,
    val kneeFlexion: Float = 0f,
    val ankleAngulation: Float = 0f,

    // ── SAFETY ──
    val kneeRotationRisk: Float = 0f,   // rotational ACL risk (snowboard-specific)
    val aclRiskMax: Float = 0f,

    // ── COMPOSITE ──
    val rideIQ: Int = 0,                // Snowboard equivalent of Ski:IQ (0-200)
    val edgingScore: Float = 0f,        // Category average
    val boardControlScore: Float = 0f,
    val pressureScore: Float = 0f,
    val balanceScore: Float = 0f,
    val styleScore: Float = 0f,         // Tricks + switch + creativity

    // ── ADVANCED ──
    val snowType: String = "Unknown",
    val carveQualityFromMic: Float = 0f,
    val fatigueIndicator: Float = 0f,
    val rideIQTrend: Float = 0f,
    val asymmetryScore: Float = 100f,   // toeside vs heelside
    val weakerEdge: String = "",

    // ── TURN PHASES ──
    val phaseInitScore: Float = 0f,
    val phaseApexScore: Float = 0f,
    val phaseTransitionScore: Float = 0f,
    val weakestPhase: String = "",

    // ── STATE ──
    val runDurationMs: Long = 0,
    val isMoving: Boolean = false,
    val currentGForce: Float = 1f,
    val currentEdgeAngle: Float = 0f,
    val currentRoll: Float = 0f,
    val currentPitch: Float = 0f,
) {
    val edgeSymmetryPercent: Int get() = (edgeSymmetry * 100).toInt()

    val runDurationFormatted: String
        get() {
            val totalSec = runDurationMs / 1000
            return "%d:%02d".format(totalSec / 60, totalSec % 60)
        }

    val rideIQLabel: String
        get() = when {
            rideIQ >= 140 -> "Expert"
            rideIQ >= 115 -> "Advanced"
            rideIQ >= 90 -> "Intermediate"
            else -> "Developing"
        }
}

package com.kineticai.app.xcski

/**
 * Sub-techniques for cross-country skiing.
 * Classic (5) + Skate (5) + transition states.
 */
enum class SubTechnique(val displayName: String, val shortName: String, val isClassic: Boolean) {
    DIAGONAL_STRIDE("Diagonal Stride", "DS", true),
    DOUBLE_POLING("Double Poling", "DP", true),
    KICK_DOUBLE_POLE("Kick Double Pole", "KDP", true),
    HERRINGBONE("Herringbone", "HRB", true),
    CLASSIC_TUCK("Tuck (Classic)", "TCK", true),

    V1_OFFSET("V1 Offset", "V1", false),
    V2_ONE_SKATE("V2 One-Skate", "V2", false),
    V2_ALTERNATE("V2 Alternate", "V2A", false),
    FREE_SKATE("Free Skate", "FS", false),
    SKATE_TUCK("Tuck (Skate)", "STK", false),

    STANDING("Standing", "STA", true),
    UNKNOWN("Unknown", "???", true);
}

enum class TerrainGrade(val displayName: String) {
    STEEP_UPHILL("Steep Uphill"),       // > 10%
    MODERATE_UPHILL("Moderate Uphill"), // 3-10%
    FLAT("Flat"),                       // -3% to 3%
    MODERATE_DOWNHILL("Moderate Down"), // -3% to -10%
    STEEP_DOWNHILL("Steep Downhill");   // < -10%

    companion object {
        fun fromGradePercent(grade: Float): TerrainGrade = when {
            grade > 10f -> STEEP_UPHILL
            grade > 3f -> MODERATE_UPHILL
            grade > -3f -> FLAT
            grade > -10f -> MODERATE_DOWNHILL
            else -> STEEP_DOWNHILL
        }
    }
}

/**
 * A single detected stride cycle with all extracted metrics.
 */
data class StrideCycle(
    val startTime: Long,
    val endTime: Long,
    val technique: SubTechnique,
    val terrainGrade: TerrainGrade,
    val gradePercent: Float,

    val cycleDurationMs: Long,
    val cycleRatePerMin: Float,
    val cycleLengthMeters: Float,

    val glideTimeRatio: Float,
    val polingTimeRatio: Float,
    val recoveryTimeRatio: Float,

    val lateralSwayDeg: Float,
    val lateralSymmetry: Float,
    val pitchOscillationDeg: Float,
    val peakAccelG: Float,

    val speedKmh: Float,
    val altitudeM: Float,
)

/**
 * Aggregate session metrics for cross-country skiing.
 */
data class XCSkiMetrics(
    val totalStrides: Int = 0,
    val totalDistanceM: Float = 0f,
    val totalClimbM: Float = 0f,
    val totalDescentM: Float = 0f,
    val elapsedMs: Long = 0L,

    val avgCycleRate: Float = 0f,
    val avgCycleLength: Float = 0f,
    val avgGlideRatio: Float = 0f,
    val avgLateralSymmetry: Float = 0f,
    val avgSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,

    val currentTechnique: SubTechnique = SubTechnique.UNKNOWN,
    val currentCycleRate: Float = 0f,
    val currentSpeedKmh: Float = 0f,
    val currentTerrainGrade: TerrainGrade = TerrainGrade.FLAT,

    val techniqueDistribution: Map<SubTechnique, Float> = emptyMap(),
    val speedByTechnique: Map<SubTechnique, Float> = emptyMap(),

    val glideScore: Float = 0f,
    val powerScore: Float = 0f,
    val symmetryScore: Float = 0f,
    val techniqueScore: Float = 0f,
    val strideScore: Float = 0f,

    val cycleRateTrend: Float = 0f,
    val fatigueFactor: Float = 0f,

    val sessionDurationMs: Long = 0L,
)

/**
 * Feature vector extracted from a single stride cycle window,
 * used by the SubTechniqueClassifier.
 */
data class CycleFeatures(
    val lateralSwayAmplitude: Float,
    val lateralSwaySymmetry: Float,
    val pitchOscillationAmplitude: Float,
    val cycleDurationMs: Long,
    val speedKmh: Float,
    val gradePercent: Float,
)

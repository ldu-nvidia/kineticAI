package com.kineticai.app.xcski

/**
 * Generates coaching feedback for cross-country skiing.
 *
 * Coaching priorities (highest first):
 *   1. Technique selection — are you using the right technique for the terrain?
 *   2. Pacing — is your cadence consistent? Are you fatiguing?
 *   3. Glide efficiency — are you getting good glide per stride?
 *   4. Symmetry — are you balanced left-to-right?
 *   5. Power application — are your pole pushes consistent?
 */
class XCSkiFeedbackGenerator {

    data class XCTip(val message: String, val category: String, val priority: Int)

    private var lastTipTime = 0L
    private var lastTipCategory = ""
    private val tipCooldownMs = 15_000L

    fun evaluate(metrics: XCSkiMetrics, timestampMs: Long): XCTip? {
        if (timestampMs - lastTipTime < tipCooldownMs) return null
        if (metrics.totalStrides < 5) return null

        val tip = findBestTip(metrics) ?: return null
        lastTipTime = timestampMs
        lastTipCategory = tip.category
        return tip
    }

    private fun findBestTip(m: XCSkiMetrics): XCTip? {
        // 1. Fatigue warning (highest priority after enough data)
        if (m.fatigueFactor > 15f) {
            return XCTip(
                "Your cadence is dropping ${String.format("%.0f", m.fatigueFactor)}% from session start. " +
                    "Consider a rest or reduce intensity.",
                "Pacing", 1
            )
        }

        // 2. Technique selection for terrain
        if (m.techniqueScore < 50f && m.totalStrides > 20) {
            val advice = terrainTechniqueAdvice(m.currentTechnique, m.currentTerrainGrade)
            if (advice != null) {
                return XCTip(advice, "Technique", 2)
            }
        }

        // 3. Glide efficiency
        if (m.glideScore < 40f && m.avgGlideRatio < 0.4f) {
            return XCTip(
                "Extend your glide phase — you're spending too much time pushing. " +
                    "Focus on a longer, balanced single-ski glide before the next push.",
                "Glide", 3
            )
        }

        // 4. Symmetry
        if (m.symmetryScore < 60f && m.avgLateralSymmetry < 0.7f) {
            return XCTip(
                "Your left/right push is unbalanced (${String.format("%.0f", m.avgLateralSymmetry * 100)}% symmetry). " +
                    "Focus on equal push power on both sides.",
                "Symmetry", 4
            )
        }

        // 5. Power consistency
        if (m.powerScore < 45f) {
            return XCTip(
                "Your cadence is inconsistent — try to maintain a steady rhythm. " +
                    "Current: ${String.format("%.0f", m.currentCycleRate)} strides/min.",
                "Power", 5
            )
        }

        // 6. Positive reinforcement
        if (m.strideScore > 140f) {
            return XCTip(
                "Strong technique! Stride Score ${String.format("%.0f", m.strideScore)}/200. " +
                    "Keep this rhythm.",
                "Overall", 6
            )
        }

        return null
    }

    private fun terrainTechniqueAdvice(current: SubTechnique, terrain: TerrainGrade): String? {
        return when (terrain) {
            TerrainGrade.STEEP_UPHILL -> when {
                current == SubTechnique.DOUBLE_POLING ->
                    "Steep uphill — switch to diagonal stride or herringbone. DP doesn't generate enough power here."
                current == SubTechnique.V2_ONE_SKATE ->
                    "Steep climb — switch to V1 offset. V2 is too energy-intensive on steep grades."
                else -> null
            }
            TerrainGrade.MODERATE_UPHILL -> when {
                current == SubTechnique.DOUBLE_POLING ->
                    "Moderate uphill — consider kick double pole or diagonal stride for more power."
                current == SubTechnique.CLASSIC_TUCK || current == SubTechnique.SKATE_TUCK ->
                    "You're tucking on an uphill — you'll lose all momentum. Start striding!"
                else -> null
            }
            TerrainGrade.FLAT -> when {
                current == SubTechnique.HERRINGBONE ->
                    "Flat terrain — no need for herringbone. Switch to double poling or diagonal stride."
                current == SubTechnique.DIAGONAL_STRIDE && true ->
                    null // DS is fine on flat
                else -> null
            }
            TerrainGrade.MODERATE_DOWNHILL -> when {
                current == SubTechnique.HERRINGBONE || current == SubTechnique.DIAGONAL_STRIDE ->
                    "Moderate downhill — switch to double poling or tuck to use gravity."
                else -> null
            }
            TerrainGrade.STEEP_DOWNHILL -> when {
                current != SubTechnique.CLASSIC_TUCK && current != SubTechnique.SKATE_TUCK ->
                    "Steep downhill — get into a tuck position to maximize speed."
                else -> null
            }
        }
    }

    fun generatePostSessionSummary(metrics: XCSkiMetrics): List<String> {
        val tips = mutableListOf<String>()

        // Session overview
        val distKm = metrics.totalDistanceM / 1000f
        val durationMin = metrics.sessionDurationMs / 60000f
        tips.add("Session: ${String.format("%.1f", distKm)} km in ${String.format("%.0f", durationMin)} min, " +
            "${metrics.totalStrides} strides")

        // Stride Score
        tips.add("Stride Score: ${String.format("%.0f", metrics.strideScore)}/200 " +
            "(Glide: ${String.format("%.0f", metrics.glideScore)}, " +
            "Power: ${String.format("%.0f", metrics.powerScore)}, " +
            "Symmetry: ${String.format("%.0f", metrics.symmetryScore)}, " +
            "Technique: ${String.format("%.0f", metrics.techniqueScore)})")

        // Averages
        tips.add("Avg cadence: ${String.format("%.0f", metrics.avgCycleRate)} strides/min, " +
            "Avg stride length: ${String.format("%.1f", metrics.avgCycleLength)} m")

        // Elevation
        tips.add("Elevation: +${String.format("%.0f", metrics.totalClimbM)} m / " +
            "-${String.format("%.0f", metrics.totalDescentM)} m")

        // Technique distribution
        if (metrics.techniqueDistribution.isNotEmpty()) {
            val top3 = metrics.techniqueDistribution.entries
                .sortedByDescending { it.value }
                .take(3)
                .joinToString(", ") { "${it.key.shortName}: ${String.format("%.0f", it.value)}%" }
            tips.add("Technique mix: $top3")
        }

        // Weakest category
        val scores = listOf(
            "Glide" to metrics.glideScore,
            "Power" to metrics.powerScore,
            "Symmetry" to metrics.symmetryScore,
            "Technique" to metrics.techniqueScore,
        )
        val weakest = scores.minByOrNull { it.second }
        if (weakest != null && weakest.second < 60f) {
            tips.add("Focus area: ${weakest.first} (${String.format("%.0f", weakest.second)}/100). " +
                when (weakest.first) {
                    "Glide" -> "Work on extending your glide phase — balance drills on one ski."
                    "Power" -> "Work on maintaining a consistent cadence — try tempo drills."
                    "Symmetry" -> "Practice pushing equally on both sides — one-ski drills help."
                    "Technique" -> "Practice matching your technique to the terrain — transitions drill."
                    else -> ""
                })
        }

        // Fatigue
        if (metrics.fatigueFactor > 10f) {
            tips.add("Fatigue detected: cadence dropped ${String.format("%.0f", metrics.fatigueFactor)}% " +
                "from session start. Consider shorter sessions or interval training to build endurance.")
        }

        // Speed by technique
        if (metrics.speedByTechnique.isNotEmpty()) {
            val fastest = metrics.speedByTechnique.maxByOrNull { it.value }
            if (fastest != null) {
                tips.add("Fastest technique: ${fastest.key.displayName} at " +
                    "${String.format("%.1f", fastest.value)} km/h avg")
            }
        }

        return tips
    }
}

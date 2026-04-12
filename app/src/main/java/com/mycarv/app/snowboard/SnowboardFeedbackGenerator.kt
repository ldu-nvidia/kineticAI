package com.mycarv.app.snowboard

/**
 * Snowboard-specific coaching feedback generator.
 *
 * Differences from ski coaching:
 *   - Toeside/heelside instead of left/right
 *   - Heelside is typically the weaker edge (addressed specifically)
 *   - Trick feedback (spin quality, landing, style)
 *   - Switch riding encouragement
 *   - Front-back balance emphasis (nose/tail pressure)
 *   - Board twist awareness
 */
class SnowboardFeedbackGenerator {

    private var lastFeedbackTime = 0L
    private val cooldownMs = 8_000L

    data class RideTip(
        val message: String,
        val priority: Priority,
    ) {
        enum class Priority { LOW, MEDIUM, HIGH }
    }

    fun evaluate(metrics: SnowboardMetrics, latestTurn: SnowboardTurn?): RideTip? {
        val now = System.currentTimeMillis()
        if (now - lastFeedbackTime < cooldownMs) return null
        if (metrics.turnCount < 3) return null

        // Safety first: knee rotation risk
        if (metrics.kneeRotationRisk > 60f) {
            lastFeedbackTime = now
            return RideTip(
                "High knee rotation detected — avoid twisting movements. Keep your knees aligned with the board direction.",
                RideTip.Priority.HIGH,
            )
        }

        // Fatigue
        if (metrics.fatigueIndicator > 25f && metrics.turnCount > 15) {
            lastFeedbackTime = now
            return RideTip(
                "Your technique is dropping (${String.format("%.0f", metrics.fatigueIndicator)}% decline). Take a break or switch to easier terrain.",
                RideTip.Priority.MEDIUM,
            )
        }

        // Toeside/heelside asymmetry
        if (metrics.asymmetryScore < 65f && metrics.weakerEdge.isNotEmpty() && metrics.turnCount > 8) {
            lastFeedbackTime = now
            return RideTip(
                "Your ${metrics.weakerEdge.lowercase()} turns are weaker (${metrics.toesideEdgeAvg.toInt()}° toe vs ${metrics.heelsideEdgeAvg.toInt()}° heel). Practice ${metrics.weakerEdge.lowercase()} garlands.",
                RideTip.Priority.MEDIUM,
            )
        }

        // Find weakest metric
        val candidates = mutableListOf<Pair<String, Float>>()
        candidates.add("edgeAngle" to metrics.edgeAngleScore)
        candidates.add("earlyEdging" to metrics.earlyEdging)
        candidates.add("edgeSymmetry" to metrics.edgeSymmetry * 100f)
        candidates.add("turnShape" to metrics.turnShape)
        candidates.add("carveQuality" to metrics.carveQuality)
        candidates.add("weightRelease" to metrics.weightRelease)
        if (metrics.bootFlexAngle != 0f) {
            candidates.add("balance" to (50f + metrics.bootFlexAngle * 3f).coerceIn(0f, 100f))
        }

        val weakest = candidates.minByOrNull { it.second } ?: return null
        if (weakest.second > 75f) return null

        lastFeedbackTime = now
        return generateTip(weakest.first, weakest.second, metrics)
    }

    private fun generateTip(metric: String, score: Float, m: SnowboardMetrics): RideTip {
        val priority = when { score < 30f -> RideTip.Priority.HIGH; score < 55f -> RideTip.Priority.MEDIUM; else -> RideTip.Priority.LOW }

        val message = when (metric) {
            "edgeAngle" ->
                if (m.edgeAngle < 20f) "Get more edge angle! Lean your whole body into the turn — like a motorcycle rider. The board won't carve without edge commitment."
                else "Good edge angle. To push higher, focus on angulation — bend at the waist and knees, not just leaning."

            "earlyEdging" ->
                "Get on edge earlier in the turn. As you cross the fall line, immediately commit to the new edge — don't ride flat between turns."

            "edgeSymmetry" ->
                "Your toeside and heelside are uneven. Practice garlands on your ${m.weakerEdge.lowercase()} to build equal confidence on both edges."

            "turnShape" ->
                "Your turns are too jerky. Focus on smooth, round arcs. Let the sidecut do the work — guide the board through a complete C-shape."

            "carveQuality" -> {
                val snowNote = if (m.snowType != "Unknown") " (on ${m.snowType})" else ""
                "You're skidding more than carving$snowNote. Roll the board onto edge by tipping it, not twisting. Clean lines in the snow = carving."
            }

            "weightRelease" ->
                "Be more dynamic between turns. Flex and extend! Compress at the end of each turn, spring up to transition, then re-engage the new edge."

            "balance" ->
                if (m.bootFlexAngle < -3f) "You're leaning back! Push your shins into the front of your boots. Weight should be centered or slightly forward."
                else "Good balance. Keep that centered stance."

            else -> "Focus on your fundamentals. Ride:IQ ${m.rideIQ}/200."
        }

        return RideTip(message, priority)
    }

    fun generatePostRunFeedback(m: SnowboardMetrics): List<String> {
        val tips = mutableListOf<String>()

        tips.add("Run: ${m.turnCount} turns (${m.toesideTurnCount} toe / ${m.heelsideTurnCount} heel), " +
            "max ${String.format("%.1f", m.maxSpeedKmh)} km/h, ${m.runDurationFormatted}")

        tips.add("Ride:IQ ${m.rideIQ}/200 — ${m.rideIQLabel}")

        tips.add("Edging: ${m.edgingScore.toInt()} | Board Control: ${m.boardControlScore.toInt()} | " +
            "Pressure: ${m.pressureScore.toInt()} | Balance: ${m.balanceScore.toInt()} | Style: ${m.styleScore.toInt()}")

        // Edge comparison
        tips.add("Toeside: avg ${String.format("%.1f", m.toesideEdgeAvg)}° | Heelside: avg ${String.format("%.1f", m.heelsideEdgeAvg)}°")
        if (m.asymmetryScore < 70f) {
            tips.add("${m.weakerEdge} is your weaker edge — practice ${m.weakerEdge.lowercase()} garlands and traverses.")
        } else {
            tips.add("Good edge symmetry (${m.asymmetryScore.toInt()}% balanced).")
        }

        // Carve quality
        if (m.carveQuality > 0) {
            tips.add("Carve quality (audio): ${m.carveQuality.toInt()}/100")
            if (m.carveQuality < 50) tips.add("Lots of scraping detected — work on clean edge engagement.")
        }

        // Tricks
        if (m.trickCount > 0) {
            tips.add("Tricks: ${m.trickCount} (best: ${m.bestTrickType.name}, max spin: ${String.format("%.0f", m.maxSpinDegrees)}°)")
            tips.add("Total airtime: ${String.format("%.1f", m.totalAirtimeMs / 1000f)}s, avg style: ${m.trickStyleAvg.toInt()}/100")
        }

        // Switch riding
        if (m.switchPercent > 5f) {
            tips.add("Switch riding: ${String.format("%.0f", m.switchPercent)}% of the run — great for progression!")
        } else if (m.turnCount > 10) {
            tips.add("Try riding switch for a few turns each run — it accelerates improvement.")
        }

        // G-force
        tips.add("Max G-force: ${String.format("%.1f", m.maxGForce)}G")

        // Snow type
        if (m.snowType != "Unknown") {
            val snowTip = when (m.snowType) {
                "Powder" -> "Powder — shift weight slightly back, keep nose up, never stop!"
                "Ice" -> "Ice — smooth edge engagement, no sudden movements."
                "Groomed" -> "Groomed — perfect for carving practice."
                "Slush" -> "Slush — weight forward, don't get caught on your tail."
                "Crud" -> "Crud — extra knee flex, stay centered, absorb everything."
                else -> null
            }
            snowTip?.let { tips.add(it) }
        }

        // Fatigue
        if (m.fatigueIndicator > 20f) {
            tips.add("Fatigue: Ride:IQ dropped ${String.format("%.0f", m.fatigueIndicator)}% during the run. Consider a break.")
        }

        return tips
    }
}

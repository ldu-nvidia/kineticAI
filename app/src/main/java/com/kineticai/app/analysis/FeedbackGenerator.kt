package com.kineticai.app.analysis

/**
 * Generates real-time coaching tips and post-run feedback based on the
 * 4 fundamental skiing skills (Balance, Edging, Rotary, Pressure).
 *
 * Real-time tips follow a professional ski coaching approach:
 *   - Identify the weakest metric (biggest improvement opportunity)
 *   - Deliver one actionable tip at a time
 *   - Cooldown between tips to avoid overload (~8s)
 *   - Chairlift tips for between-run coaching
 *
 * Coaching language is modeled after PSIA, and professional
 * ski instructor phrasing.
 */
class FeedbackGenerator {

    private var lastFeedbackTime = 0L
    private val cooldownMs = 8_000L

    data class CoachingTip(
        val message: String,
        val priority: Priority,
        val category: SkillCategory,
    ) {
        enum class Priority { LOW, MEDIUM, HIGH }
    }

    enum class SkillCategory { BALANCE, EDGING, ROTARY, PRESSURE }

    // ──────────────────────────────────────────────
    //  REAL-TIME COACHING (during run)
    // ──────────────────────────────────────────────

    fun evaluate(metrics: SkiMetrics, latestTurn: DetectedTurn?): CoachingTip? {
        val now = System.currentTimeMillis()
        if (now - lastFeedbackTime < cooldownMs) return null
        if (metrics.turnCount < 3) return null

        val tip = findWeakestMetricTip(metrics, latestTurn) ?: return null
        lastFeedbackTime = now
        return tip
    }

    /**
     * Find the single weakest metric across all 4 categories and generate
     * one targeted coaching tip. This mirrors the "focus skill" coaching approach.
     */
    private fun findWeakestMetricTip(metrics: SkiMetrics, turn: DetectedTurn?): CoachingTip? {
        data class MetricEntry(val name: String, val score: Float, val cat: SkillCategory)

        val candidates = mutableListOf<MetricEntry>()

        if (metrics.turnCount >= 3) {
            candidates.add(MetricEntry("earlyForward", metrics.earlyForwardMovement, SkillCategory.BALANCE))
            candidates.add(MetricEntry("centeredBalance", metrics.centeredBalance, SkillCategory.BALANCE))
            candidates.add(MetricEntry("edgeAngle", metrics.edgeAngleScore, SkillCategory.EDGING))
            candidates.add(MetricEntry("earlyEdging", metrics.earlyEdging, SkillCategory.EDGING))
            candidates.add(MetricEntry("edgeSimilarity", metrics.edgeSimilarity, SkillCategory.EDGING))
            candidates.add(MetricEntry("progressiveEdge", metrics.progressiveEdgeBuild, SkillCategory.EDGING))
            candidates.add(MetricEntry("turnShape", metrics.turnShape, SkillCategory.ROTARY))
            candidates.add(MetricEntry("pressureSmoothness", metrics.pressureSmoothness, SkillCategory.PRESSURE))
            candidates.add(MetricEntry("weightRelease", metrics.transitionWeightRelease, SkillCategory.PRESSURE))

            // Extended sensor metrics (only if data available)
            if (metrics.bootFlexAngle != 0f) {
                val flexScore = (metrics.bootFlexAngle * 3f + 50f).coerceIn(0f, 100f)
                candidates.add(MetricEntry("bootFlex", flexScore, SkillCategory.BALANCE))
            }
            if (metrics.ankleAngulation != 0f) {
                val angScore = (metrics.ankleAngulation * 4f).coerceIn(0f, 100f)
                candidates.add(MetricEntry("angulation", angScore, SkillCategory.EDGING))
            }
            if (metrics.kneeFlexion > 0f) {
                val kneeScore = if (metrics.kneeFlexion in 15f..45f) 80f
                    else if (metrics.kneeFlexion < 10f) 30f else 50f
                candidates.add(MetricEntry("kneeFlex", kneeScore, SkillCategory.BALANCE))
            }
            if (metrics.kinChainBottomUpPct > 0f) {
                candidates.add(MetricEntry("kinChain", metrics.kinChainBottomUpPct, SkillCategory.ROTARY))
            }
            if (metrics.carveQualityScore > 0f) {
                candidates.add(MetricEntry("carveQuality", metrics.carveQualityScore, SkillCategory.EDGING))
            }
        }

        // ACL warning overrides everything — safety first
        if (metrics.aclRiskMax > 60f) {
            lastFeedbackTime = System.currentTimeMillis()
            return CoachingTip(
                "Knee collapsing inward — focus on keeping knees tracking over toes. " +
                    "This protects your ACL.",
                CoachingTip.Priority.HIGH, SkillCategory.BALANCE,
            )
        }

        // Fatigue warning — second priority
        if (metrics.fatigueIndicator > 25f && metrics.turnCount > 15) {
            lastFeedbackTime = System.currentTimeMillis()
            return CoachingTip(
                "Your technique is declining (${String.format("%.0f", metrics.fatigueIndicator)}% drop from start). " +
                    "Consider taking a break or doing easier terrain.",
                CoachingTip.Priority.MEDIUM, SkillCategory.BALANCE,
            )
        }

        // Asymmetry coaching — every ~20 turns, highlight the weaker side
        if (metrics.turnCount > 10 && metrics.turnCount % 20 < 3 &&
            metrics.asymmetryScore < 70f && metrics.weakerSide.isNotEmpty()
        ) {
            lastFeedbackTime = System.currentTimeMillis()
            return CoachingTip(
                "Your ${metrics.weakerSide.lowercase()} turns are weaker " +
                    "(${metrics.weakerSideMetric} asymmetry). " +
                    "Focus on matching your strong side — practice ${metrics.weakerSide.lowercase()} turns only.",
                CoachingTip.Priority.MEDIUM, SkillCategory.EDGING,
            )
        }

        // Phase-specific coaching — identify weakest turn phase
        if (metrics.weakestPhase.isNotEmpty() && metrics.turnCount > 8) {
            val phaseScore = when (metrics.weakestPhase) {
                "Initiation" -> metrics.phaseInitScore
                "Steering In" -> metrics.phaseSteerInScore
                "Apex" -> metrics.phaseApexScore
                "Steering Out" -> metrics.phaseSteerOutScore
                "Transition" -> metrics.phaseTransitionScore
                else -> 100f
            }
            if (phaseScore < 40f) {
                candidates.add(MetricEntry("phase_${metrics.weakestPhase}", phaseScore, SkillCategory.BALANCE))
            }
        }

        if (candidates.isEmpty()) return null

        val weakest = candidates.minByOrNull { it.score } ?: return null
        if (weakest.score > 75f) return null

        return generateTipForMetric(weakest.name, weakest.score, weakest.cat, metrics, turn)
    }

    private fun generateTipForMetric(
        metric: String,
        score: Float,
        cat: SkillCategory,
        metrics: SkiMetrics,
        turn: DetectedTurn?,
    ): CoachingTip {
        val priority = when {
            score < 30f -> CoachingTip.Priority.HIGH
            score < 55f -> CoachingTip.Priority.MEDIUM
            else -> CoachingTip.Priority.LOW
        }

        val message = when (metric) {
            "earlyForward" ->
                "Project your body forward into each new turn. Think about moving your hips " +
                    "over the front of your boots as you initiate."

            "centeredBalance" ->
                "Stay centered over your bindings through the turn. Avoid leaning too far " +
                    "back — keep your shins pressing the front of your boots."

            "edgeAngle" -> {
                val angle = metrics.edgeAngle
                if (angle < 20f)
                    "Your edge angle is low (${String.format("%.0f", angle)}°). Lean into " +
                        "the turn more — like a motorcycle rider tipping into a corner."
                else
                    "Good edge angle. To push higher, focus on angulation — move your hips " +
                        "inside while keeping shoulders outside the turn."
            }

            "earlyEdging" ->
                "Get on edge earlier in the turn. Allow yourself to 'topple' into the new turn — " +
                    "trust that your skis will catch you. This unlocks tighter, more dynamic arcs."

            "edgeSimilarity" -> {
                val stronger = if (metrics.leftTurnCount > 0 || metrics.rightTurnCount > 0) {
                    val leftAvg = metrics.edgeAngle // simplified
                    "your weaker side"
                } else "both sides"
                "Your left and right turns have different edge angles. Practice $stronger " +
                    "to build symmetry — try an outside-ski-only drill."
            }

            "progressiveEdge" ->
                "Don't 'park and ride' on a fixed edge angle. Keep building your edge " +
                    "through the turn — think of it as a dimmer switch, not a light switch. " +
                    "This creates tighter, more controlled arcs."

            "turnShape" ->
                "Your turns are more Z-shaped than C-shaped. Focus on making smooth, " +
                    "round arcs. Let the ski's sidecut do the work — guide it through " +
                    "a complete C-shape rather than abrupt direction changes."

            "pressureSmoothness" ->
                "Your pressure application is jerky. Think about gradually 'pedaling' " +
                    "onto your outside ski through the turn rather than stomping on it all at once."

            "weightRelease" ->
                "Be more dynamic between turns. Flex and retract your legs at the " +
                    "transition to unweight your skis — this creates the bounce and flow " +
                    "that makes expert skiing look effortless."

            "bootFlex" ->
                if (metrics.bootFlexAngle < 0)
                    "You're sitting back! Drive your shins forward into the boot tongues. " +
                        "Forward lean is essential for ski control."
                else
                    "Good forward pressure. Keep driving your shins into the boot."

            "angulation" ->
                "Work on ankle angulation — tilt your ankles into the turn independently " +
                    "of your body lean. Think motorcycle rider: hips in, shoulders out."

            "kneeFlex" ->
                if (metrics.kneeFlexion < 15)
                    "Your legs are too straight. Flex your knees more — find an athletic " +
                        "stance like a basketball defender. This gives you shock absorption."
                else if (metrics.kneeFlexion > 50)
                    "You're sitting too low. Straighten up slightly — over-flexing " +
                        "puts you in the back seat and tires your quads."
                else
                    "Good knee flex. Maintain this athletic stance."

            "kinChain" ->
                "Initiate turns from your feet, not your upper body. Expert skiing " +
                    "starts from the bottom up — tip your skis first, let the body follow. " +
                    "Think: feet lead, body follows."

            "carveQuality" -> {
                val snowNote = if (metrics.snowType != "Unknown") " (on ${metrics.snowType})" else ""
                if (metrics.carveQualityScore < 40)
                    "You're skidding more than carving$snowNote. Roll onto your edges " +
                        "and let the sidecut do the turning. Less steering, more tipping."
                else
                    "Decent carving$snowNote. Keep working on clean edge engagement " +
                        "for those railroad-track arcs."
            }

            "phase_Initiation" ->
                "Your turn initiation is weak. Focus on the first moment: topple into the turn, " +
                    "transfer weight early, and get on edge before the fall line."

            "phase_Steering In" ->
                "The steering-in phase needs work. After initiating, keep building your edge angle " +
                    "progressively — think dimmer switch, not light switch."

            "phase_Apex" ->
                "Your turn apex is underpowered. At the midpoint of the turn, you should be at " +
                    "peak edge angle and peak G-force. Commit more through the belly of the turn."

            "phase_Steering Out" ->
                "You're releasing too early. Sustain your edge angle and turn shape through the " +
                    "second half of the turn. Don't rush to the next turn."

            "phase_Transition" ->
                "Your transition between turns is weak. Focus on active unweighting — flex and " +
                    "retract your legs to create lightness. Use the rebound energy."

            else ->
                "Focus on your technique fundamentals. Score: ${String.format("%.0f", score)}"
        }

        return CoachingTip(message, priority, cat)
    }

    // ──────────────────────────────────────────────
    //  POST-RUN FEEDBACK (detailed analysis)
    // ──────────────────────────────────────────────

    fun generatePostRunFeedback(metrics: SkiMetrics, turns: List<DetectedTurn>): List<String> {
        val tips = mutableListOf<String>()

        // Run summary
        tips.add(
            "Run: ${metrics.turnCount} turns over ${metrics.runDurationFormatted}, " +
                "max speed ${String.format("%.1f", metrics.maxSpeedKmh)} km/h, " +
                "${String.format("%.0f", metrics.altitudeDrop)}m vertical drop"
        )

        // Kinetic Score headline
        val iqLabel = when {
            metrics.skiIQ >= 140 -> "Expert"
            metrics.skiIQ >= 115 -> "Advanced"
            metrics.skiIQ >= 90 -> "Intermediate"
            else -> "Developing"
        }
        tips.add("Kinetic Score ${metrics.skiIQ}/200 — $iqLabel level")

        // Category breakdown
        tips.add(
            "Balance: ${metrics.balanceScore.toInt()} | " +
                "Edging: ${metrics.edgingScore.toInt()} | " +
                "Rotary: ${metrics.rotaryScore.toInt()} | " +
                "Pressure: ${metrics.pressureScore.toInt()}"
        )

        // Identify top strength and weakness
        data class CatScore(val name: String, val score: Float)
        val categories = listOf(
            CatScore("Balance", metrics.balanceScore),
            CatScore("Edging", metrics.edgingScore),
            CatScore("Rotary", metrics.rotaryScore),
            CatScore("Pressure", metrics.pressureScore),
        )
        val strongest = categories.maxByOrNull { it.score }
        val weakest = categories.minByOrNull { it.score }

        strongest?.let {
            tips.add("Strongest: ${it.name} (${it.score.toInt()}/100)")
        }
        weakest?.let {
            tips.add("Focus area: ${it.name} (${it.score.toInt()}/100)")
        }

        // Turn balance
        if (metrics.turnSymmetry < 0.6f) {
            val dominant = if (metrics.leftTurnCount > metrics.rightTurnCount) "left" else "right"
            tips.add(
                "Turn balance: ${metrics.leftTurnCount}L / ${metrics.rightTurnCount}R — " +
                    "you're favoring $dominant turns. Practice your weaker side with " +
                    "single-turn drills."
            )
        } else {
            tips.add("Good turn balance: ${metrics.leftTurnCount}L / ${metrics.rightTurnCount}R")
        }

        // Edge angle analysis
        if (turns.isNotEmpty()) {
            tips.add("Average edge angle: ${String.format("%.1f", metrics.edgeAngle)}°")
            when {
                metrics.edgeAngle < 15 ->
                    tips.add("Low edge angle. Focus on inclination — lean into your turns more.")
                metrics.edgeAngle < 30 ->
                    tips.add("Moderate edging. To carve tighter, work on angulation and toppling early.")
                metrics.edgeAngle < 50 ->
                    tips.add("Strong edging! Focus on sustaining it through the turn (progressive edge build).")
                else ->
                    tips.add("Very aggressive edging. Impressive — make sure balance stays centered.")
            }
        }

        // Early edging
        if (metrics.earlyEdging < 50) {
            tips.add(
                "Early edging needs work (${metrics.earlyEdging.toInt()}/100). " +
                    "Practice toppling into the turn — let gravity pull you into the new turn earlier."
            )
        }

        // Progressive edge
        if (metrics.progressiveEdgeBuild < 50) {
            tips.add(
                "You're not sustaining edge angle through the turn. " +
                    "Try closing your turns more — make complete C-shapes, not park-and-ride."
            )
        }

        // G-force
        tips.add("Peak G-force: ${String.format("%.1f", metrics.maxGForce)}G")
        when {
            metrics.maxGForce > 3f ->
                tips.add("Impressive G-force! That's space shuttle takeoff territory.")
            metrics.maxGForce > 2f ->
                tips.add("Good dynamic skiing. Keep building G-force with tighter, faster carves.")
            else ->
                tips.add("Low G-force. Try steeper terrain with shorter-radius turns to build it up.")
        }

        // Weight release
        if (metrics.transitionWeightRelease < 40) {
            tips.add(
                "Your transitions are static. Practice hop turns or retraction drills " +
                    "to develop lighter, more dynamic edge changes."
            )
        }

        // ── Extended Sensor Insights ──

        // Snow type context
        if (metrics.snowType != "Unknown") {
            val snowTip = when (metrics.snowType) {
                "Powder" -> "Powder detected — great for working on rhythm and bounce. " +
                    "Focus on equal weighting and retraction turns."
                "Ice" -> "Icy conditions — focus on smooth, progressive edge engagement. " +
                    "Avoid sudden movements that break edge grip."
                "Groomed" -> "Groomed corduroy — ideal for carving practice. " +
                    "Push your edge angles and work on clean arcs."
                "Slush" -> "Slushy conditions — keep your weight forward. " +
                    "Slush catches your tails if you sit back."
                "Crud" -> "Variable snow — stay flexible and absorb with your legs. " +
                    "Keep an athletic stance with extra knee flex."
                else -> null
            }
            snowTip?.let { tips.add(it) }
        }

        // Boot flex / forward lean
        if (metrics.bootFlexAngle != 0f) {
            if (metrics.bootFlexAngle < -5f) {
                tips.add(
                    "You were leaning back for much of the run (boot flex: " +
                        "${String.format("%.0f", metrics.bootFlexAngle)}°). " +
                        "Work on driving shins into boot tongues throughout every turn."
                )
            } else if (metrics.bootFlexAngle > 8f) {
                tips.add("Great forward pressure (${String.format("%.0f", metrics.bootFlexAngle)}°) — your shin engagement is strong.")
            }
        }

        // Angulation
        if (metrics.ankleAngulation > 0f) {
            if (metrics.ankleAngulation < 5f) {
                tips.add(
                    "Low ankle angulation (${String.format("%.0f", metrics.ankleAngulation)}°). " +
                        "Work on separating ankle tilt from body lean for better edge control."
                )
            } else if (metrics.ankleAngulation > 15f) {
                tips.add("Excellent ankle angulation (${String.format("%.0f", metrics.ankleAngulation)}°) — advanced edge technique.")
            }
        }

        // Knee data
        if (metrics.kneeFlexion > 0f) {
            tips.add("Knee flexion: ${String.format("%.0f", metrics.kneeFlexion)}°")
            if (metrics.kneeFlexion < 15f) {
                tips.add("Your legs were too straight. Flex more for better absorption and control.")
            }
        }

        // ACL safety
        if (metrics.aclRiskMax > 40f) {
            tips.add(
                "Knee valgus detected (max ${String.format("%.0f", metrics.kneeValgusMax)}°, " +
                    "risk score ${String.format("%.0f", metrics.aclRiskMax)}/100). " +
                    "Focus on keeping knees tracking over toes, especially in hard turns."
            )
        }

        // Kinetic chain
        if (metrics.kinChainBottomUpPct > 0f) {
            tips.add(
                "Kinetic chain: ${String.format("%.0f", metrics.kinChainBottomUpPct)}% " +
                    "bottom-up initiation (expert pattern)."
            )
            if (metrics.kinChainBottomUpPct < 40f) {
                tips.add("Try initiating more turns from your feet — tip the skis first, let the body follow.")
            }
        }

        // Carve quality from mic
        if (metrics.carveQualityScore > 0f) {
            tips.add("Carve quality: ${String.format("%.0f", metrics.carveQualityScore)}/100 (from audio analysis)")
            if (metrics.carveQualityScore < 50f) {
                tips.add("Lots of scraping/skidding detected. Focus on rolling onto edges rather than twisting your feet.")
            }
        }

        // Jumps / airtime
        if (metrics.jumpCount > 0) {
            val airtimeSec = metrics.totalAirtimeMs / 1000f
            tips.add(
                "Caught ${metrics.jumpCount} jump${if (metrics.jumpCount > 1) "s" else ""} " +
                    "with ${String.format("%.1f", airtimeSec)}s total airtime!"
            )
        }

        // ── Turn Phase Breakdown ──
        if (metrics.turnCount >= 5) {
            tips.add(
                "Turn Phase Scores: Init ${metrics.phaseInitScore.toInt()} | " +
                    "SteerIn ${metrics.phaseSteerInScore.toInt()} | " +
                    "Apex ${metrics.phaseApexScore.toInt()} | " +
                    "SteerOut ${metrics.phaseSteerOutScore.toInt()} | " +
                    "Transition ${metrics.phaseTransitionScore.toInt()}"
            )
            if (metrics.weakestPhase.isNotEmpty()) {
                tips.add("Weakest phase: ${metrics.weakestPhase} — focus your practice here.")
            }
        }

        // ── Asymmetry Analysis ──
        if (metrics.leftTurnCount >= 3 && metrics.rightTurnCount >= 3) {
            tips.add(
                "Left turns: edge ${String.format("%.1f", metrics.leftEdgeAngleAvg)}° / " +
                    "G ${String.format("%.1f", metrics.leftGForceAvg)}G  |  " +
                    "Right turns: edge ${String.format("%.1f", metrics.rightEdgeAngleAvg)}° / " +
                    "G ${String.format("%.1f", metrics.rightGForceAvg)}G"
            )
            if (metrics.asymmetryScore < 70f) {
                tips.add(
                    "Significant asymmetry (${metrics.asymmetryScore.toInt()}% symmetry). " +
                        "${metrics.weakerSide} turns are weaker in ${metrics.weakerSideMetric}. " +
                        "Practice your weak side with single-direction drills."
                )
            } else {
                tips.add("Good turn symmetry (${metrics.asymmetryScore.toInt()}%).")
            }
        }

        // ── Fatigue Report ──
        if (metrics.turnCount >= 15) {
            when {
                metrics.fatigueIndicator > 30f ->
                    tips.add(
                        "Fatigue detected: Kinetic Score dropped ${String.format("%.0f", metrics.fatigueIndicator)}% " +
                            "from the start of this run. Your early turns were Kinetic Score " +
                            "${String.format("%.0f", metrics.earlyRunKineticScore)} vs recent " +
                            "${String.format("%.0f", metrics.currentWindowKineticScore)}. Take a break!"
                    )
                metrics.skiIQTrend > 5f ->
                    tips.add("You improved during this run! Kinetic Score trend: +${String.format("%.0f", metrics.skiIQTrend)}.")
                metrics.skiIQTrend < -5f ->
                    tips.add("Kinetic Score declined during this run (${String.format("%.0f", metrics.skiIQTrend)}). Normal fatigue or conditions changed.")
            }
        }

        // ── Personal Progress ──
        if (metrics.personalBestKineticScore > 0) {
            tips.add("Personal best Kinetic Score: ${metrics.personalBestKineticScore}")
            if (metrics.improvementPct > 5f) {
                tips.add("You're ${String.format("%.0f", metrics.improvementPct)}% above your baseline — great improvement!")
            }
        }

        return tips
    }

    // ──────────────────────────────────────────────
    //  CHAIRLIFT TIPS (between runs)
    // ──────────────────────────────────────────────

    fun generateChairliftTip(metrics: SkiMetrics): String? {
        if (metrics.turnCount < 3) return null

        data class MetricTip(val score: Float, val tip: String)
        val weaknesses = mutableListOf(
            MetricTip(metrics.earlyEdging,
                "On your next run, focus on getting on edge earlier. Think: topple into the turn."),
            MetricTip(metrics.progressiveEdgeBuild,
                "Try to hold your edge angle longer through each turn. Think: dimmer switch, not light switch."),
            MetricTip(metrics.turnShape,
                "Make rounder turns. Draw C-shapes in the snow, not Z-shapes."),
            MetricTip(metrics.transitionWeightRelease,
                "Be bouncier between turns. Flex your legs at the transition to unweight."),
            MetricTip(metrics.centeredBalance,
                "Stay more centered. Keep your shins pressing the boot tongues."),
            MetricTip(metrics.earlyForwardMovement,
                "Project forward at the start of each turn. Move your center of mass down the fall line."),
        )

        // Add extended sensor tips
        if (metrics.bootFlexAngle < -3f) {
            weaknesses.add(MetricTip(20f,
                "You were sitting back last run. Next run: drive shins forward into the boot tongues from the first turn."))
        }
        if (metrics.carveQualityScore in 1f..50f) {
            weaknesses.add(MetricTip(metrics.carveQualityScore,
                "Audio detected scraping — you were skidding. Next run: roll ankles to tip skis on edge, don't twist."))
        }
        if (metrics.kinChainBottomUpPct in 1f..40f) {
            weaknesses.add(MetricTip(metrics.kinChainBottomUpPct,
                "Your upper body is leading your turns. Next run: initiate from the feet — tip the skis first."))
        }

        // Snow-adapted tip
        val snowTip = when (metrics.snowType) {
            "Ice" -> MetricTip(30f, "Ice ahead! Focus on smooth, progressive edge engagement. No sudden movements.")
            "Powder" -> MetricTip(50f, "Powder! Equal weight both skis, bounce between turns, keep speed up.")
            "Crud" -> MetricTip(40f, "Variable snow coming. Extra knee flex, stay athletic, absorb with your legs.")
            else -> null
        }
        snowTip?.let { weaknesses.add(it) }

        return weaknesses
            .filter { it.score < 70f }
            .minByOrNull { it.score }
            ?.tip
    }
}

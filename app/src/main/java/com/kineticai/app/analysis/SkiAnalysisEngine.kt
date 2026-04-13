package com.kineticai.app.analysis

import com.kineticai.app.sensor.ImuSample
import com.kineticai.app.sensor.LocationSample
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Central analysis engine that fuses IMU orientation (via Madgwick AHRS),
 * GPS data, and barometric pressure to produce the 12 ski technique metrics
 * in real-time.
 *
 * Processing pipeline per sample:
 *   1. Madgwick filter update → quaternion orientation
 *   2. Euler extraction → roll (edge angle), pitch (fore-aft), yaw (heading)
 *   3. Gravity removal → linear acceleration in Earth frame
 *   4. Turn detection (gyro-Z zero-crossings with hysteresis)
 *   5. Per-turn metric extraction (12 metrics)
 *   6. Running aggregate metrics + Kinetic Score computation
 */
class SkiAnalysisEngine(sampleRateHz: Float = 50f) {

    private val ahrs = MadgwickFilter(beta = 0.05f, samplePeriod = 1f / sampleRateHz)
    private val turnDetector = TurnDetector(sampleRateHz = sampleRateHz)
    val feedbackGenerator = FeedbackGenerator()

    private val turns = mutableListOf<DetectedTurn>()
    private val speedSamples = mutableListOf<Float>()

    private var startTime = 0L
    private var lastTimestamp = 0L
    private var startAltitude: Double? = null
    private var maxAltitude = 0.0
    private var lastLocation: LocationSample? = null
    private var totalDistance = 0f
    private var maxSpeed = 0f
    private var maxGForce = 0f
    private var sampleCount = 0
    private var initialized = false

    // Calibration state
    private var calibrationSamples = 0
    private var calibrationAccelSum = floatArrayOf(0f, 0f, 0f)
    private val CALIBRATION_SAMPLES = 50

    // ── Asymmetry tracking ──
    private val leftTurnEdges = mutableListOf<Float>()
    private val rightTurnEdges = mutableListOf<Float>()
    private val leftTurnGForces = mutableListOf<Float>()
    private val rightTurnGForces = mutableListOf<Float>()
    private val leftTurnEarlyEdging = mutableListOf<Float>()
    private val rightTurnEarlyEdging = mutableListOf<Float>()

    // ── Fatigue tracking ──
    private val skiIQPerTurnWindow = mutableListOf<Float>()
    private var earlyRunKineticScoreSum = 0f
    private var earlyRunKineticScoreCount = 0
    private var earlyRunKineticScoreAvg = 0f

    // ── Adaptive scoring ──
    private var personalBestKineticScore = 0
    private var baselineTurnScores = mutableListOf<Float>()
    private var personalBaseline = 0f

    private var _metrics = SkiMetrics()
    val metrics: SkiMetrics get() = _metrics

    val allTurns: List<DetectedTurn> get() = turns.toList()

    fun reset() {
        ahrs.reset()
        turnDetector.reset()
        turns.clear()
        speedSamples.clear()
        startTime = 0L
        lastTimestamp = 0L
        startAltitude = null
        maxAltitude = 0.0
        lastLocation = null
        totalDistance = 0f
        maxSpeed = 0f
        maxGForce = 0f
        sampleCount = 0
        initialized = false
        calibrationSamples = 0
        calibrationAccelSum = floatArrayOf(0f, 0f, 0f)
        _metrics = SkiMetrics()
    }

    /**
     * Process one IMU sample through the full pipeline.
     * Returns a DetectedTurn if one was just completed, null otherwise.
     */
    fun processImu(sample: ImuSample): DetectedTurn? {
        if (startTime == 0L) startTime = sample.timestamp

        // ── Step 0: Static calibration (first ~1s) ──
        if (!initialized) {
            calibrationAccelSum[0] += sample.accelX
            calibrationAccelSum[1] += sample.accelY
            calibrationAccelSum[2] += sample.accelZ
            calibrationSamples++

            if (calibrationSamples >= CALIBRATION_SAMPLES) {
                val avgAx = calibrationAccelSum[0] / calibrationSamples
                val avgAy = calibrationAccelSum[1] / calibrationSamples
                val avgAz = calibrationAccelSum[2] / calibrationSamples
                ahrs.initFromAccel(avgAx, avgAy, avgAz)
                initialized = true
            }
            lastTimestamp = sample.timestamp
            sampleCount++
            return null
        }

        // ── Step 1: Compute dt ──
        val dt = if (lastTimestamp > 0) {
            ((sample.timestamp - lastTimestamp) / 1000f).coerceIn(0.001f, 0.1f)
        } else 0.02f
        lastTimestamp = sample.timestamp
        sampleCount++

        // ── Step 2: Madgwick AHRS update ──
        // Android sensors: accel in m/s², gyro in rad/s
        ahrs.update(
            gx = sample.gyroX, gy = sample.gyroY, gz = sample.gyroZ,
            ax = sample.accelX, ay = sample.accelY, az = sample.accelZ,
            dt = dt,
        )

        // ── Step 3: Extract Euler angles ──
        val roll = ahrs.rollDeg
        val pitch = ahrs.pitchDeg

        // ── Step 4: Gravity removal → linear acceleration in Earth frame ──
        val earthAccel = ahrs.rotateToEarth(sample.accelX, sample.accelY, sample.accelZ)
        val linearX = earthAccel[0]          // East
        val linearY = earthAccel[1]          // North
        val linearZ = earthAccel[2] - 9.81f  // Up (gravity removed)

        val verticalG = (earthAccel[2]) / 9.81f
        val lateralG = sqrt(linearX * linearX + linearY * linearY) / 9.81f

        // ── Step 5: Turn detection ──
        val turn = turnDetector.processSample(
            timestamp = sample.timestamp,
            gyroZ = sample.gyroZ,
            rollDeg = roll,
            pitchDeg = pitch,
            verticalG = verticalG,
            lateralG = lateralG,
        )

        turn?.let {
            turns.add(it)
            // Track per-side metrics for asymmetry analysis
            if (it.direction == TurnDirection.LEFT) {
                leftTurnEdges.add(it.edgeAngle)
                leftTurnGForces.add(it.gForce)
                leftTurnEarlyEdging.add(it.earlyEdgingScore)
            } else {
                rightTurnEdges.add(it.edgeAngle)
                rightTurnGForces.add(it.gForce)
                rightTurnEarlyEdging.add(it.earlyEdgingScore)
            }
        }

        if (lateralG > maxGForce) maxGForce = lateralG

        // ── Step 6: Update aggregate metrics ──
        updateMetrics(sample.timestamp, roll, pitch, lateralG)

        return turn
    }

    // ── Extended sensor state (from BLE boot sensors) ──
    private var currentBootFlex = 0f
    private var currentAngulation = 0f
    private var currentKneeFlexion = 0f
    private var maxKneeValgus = 0f
    private var maxAclRisk = 0f
    private var bottomUpTurnCount = 0
    private var separationSum = 0f
    private var separationCount = 0
    private var carveScoreSum = 0f
    private var carveScoreCount = 0
    private var currentSnowType = "Unknown"
    private var jumpCount = 0
    private var totalAirtimeMs = 0L
    private var airborneStartTime = 0L
    private var wasAirborne = false

    /**
     * Feed dual/tri-segment IMU data from boot sensors.
     * Called by the service when BLE data arrives.
     */
    fun processBootFlex(bootFlex: Float, angulation: Float) {
        currentBootFlex = bootFlex
        currentAngulation = angulation
    }

    fun processKneeData(kneeFlexion: Float, kneeValgus: Float, aclRisk: Float,
                        chainOrder: Int, separation: Float) {
        currentKneeFlexion = kneeFlexion
        if (kotlin.math.abs(kneeValgus) > maxKneeValgus) maxKneeValgus = kotlin.math.abs(kneeValgus)
        if (aclRisk > maxAclRisk) maxAclRisk = aclRisk
        if (chainOrder == 0) bottomUpTurnCount++ // 0 = bottom-up (expert)
        separationSum += separation
        separationCount++
    }

    fun processCarveQuality(quality: Int) {
        // quality: 1=clean, 2=moderate, 3=skidding, 4=chatter
        val score = when (quality) {
            1 -> 100f; 2 -> 65f; 3 -> 30f; 4 -> 10f; else -> 50f
        }
        carveScoreSum += score
        carveScoreCount++
    }

    fun processSnowType(type: String) {
        currentSnowType = type
    }

    fun processAirborne(isAirborne: Boolean, timestamp: Long) {
        if (isAirborne && !wasAirborne) {
            airborneStartTime = timestamp
            jumpCount++
        } else if (!isAirborne && wasAirborne && airborneStartTime > 0) {
            totalAirtimeMs += timestamp - airborneStartTime
        }
        wasAirborne = isAirborne
    }

    fun processLocation(loc: LocationSample) {
        if (startAltitude == null) startAltitude = loc.altitude
        if (loc.altitude > maxAltitude) maxAltitude = loc.altitude

        lastLocation?.let { prev ->
            totalDistance += distanceBetween(
                prev.latitude, prev.longitude,
                loc.latitude, loc.longitude,
            )
        }
        lastLocation = loc

        if (loc.speed > maxSpeed) maxSpeed = loc.speed
        speedSamples.add(loc.speed)
    }

    private fun updateMetrics(timestamp: Long, roll: Float, pitch: Float, lateralG: Float) {
        val duration = timestamp - startTime
        val leftTurns = turns.count { it.direction == TurnDirection.LEFT }
        val rightTurns = turns.count { it.direction == TurnDirection.RIGHT }

        // ── Aggregate per-turn scores ──
        val avgEdgeAngle = turnsAvg { it.edgeAngle }
        val avgEarlyEdging = turnsAvg { it.earlyEdgingScore }
        val avgProgressiveEdge = turnsAvg { it.progressiveEdgeScore }
        val avgTurnShape = turnsAvg { it.turnShapeScore }
        val avgGForce = turnsAvg { it.gForce }
        val avgWeightRelease = turnsAvg { it.weightReleaseScore }
        val avgPressureSmoothness = turnsAvg { it.pressureSmoothnessScore }
        val avgEarlyForward = turnsAvg { it.earlyForwardScore }
        val avgCenteredBalance = turnsAvg { it.centeredBalanceScore }

        // ── Edge Similarity: |avgLeft - avgRight| / max ──
        val leftEdge = turns.filter { it.direction == TurnDirection.LEFT }.map { it.edgeAngle }
        val rightEdge = turns.filter { it.direction == TurnDirection.RIGHT }.map { it.edgeAngle }
        val avgLeftEdge = if (leftEdge.isNotEmpty()) leftEdge.average().toFloat() else 0f
        val avgRightEdge = if (rightEdge.isNotEmpty()) rightEdge.average().toFloat() else 0f
        val maxEdge = maxOf(avgLeftEdge, avgRightEdge, 1f)
        val edgeSimilarity = (100f * (1f - abs(avgLeftEdge - avgRightEdge) / maxEdge)).coerceIn(0f, 100f)

        // ── Edge Angle Score: normalize to 0-100 (0°→0, 60°→100) ──
        val edgeAngleScore = (avgEdgeAngle / 60f * 100f).coerceIn(0f, 100f)

        // ── Parallel Skis: proxy via turn shape smoothness + edge similarity ──
        val parallelScore = (avgTurnShape * 0.6f + edgeSimilarity * 0.4f).coerceIn(0f, 100f)

        // ── Early Weight Transfer: proxy from early edging (correlated) ──
        val earlyWeightTransfer = avgEarlyEdging * 0.9f

        // ── Outside Ski Pressure: estimated from lateral G magnitude ──
        val outsideSkiPressure = (avgGForce / 3f * 100f).coerceIn(0f, 100f)

        // ── Extended sensor metrics ──
        // Boot flex score: positive flex = forward lean = good (0-100)
        val bootFlexScore = (currentBootFlex * 3f + 50f).coerceIn(0f, 100f)
        // Angulation score: higher = better ankle technique (0-100)
        val angulationScore = (currentAngulation * 4f).coerceIn(0f, 100f)
        // Carve quality from mic (0-100)
        val avgCarveScore = if (carveScoreCount > 0) carveScoreSum / carveScoreCount else 50f
        // Kinetic chain: % of bottom-up initiations
        val bottomUpPct = if (turns.isNotEmpty()) (bottomUpTurnCount.toFloat() / turns.size * 100f) else 0f
        // Separation average
        val avgSeparation = if (separationCount > 0) separationSum / separationCount else 0f

        // ── Category Averages (enhanced with extended sensors) ──
        // Balance: now includes boot flex (forward lean from dual IMU)
        val balanceScore = if (currentBootFlex != 0f) {
            (avgEarlyForward * 0.3f + avgCenteredBalance * 0.3f + bootFlexScore * 0.4f)
        } else {
            (avgEarlyForward + avgCenteredBalance) / 2f
        }

        // Edging: now includes angulation (from dual IMU) and carve quality (from mic)
        val edgingBase = (edgeAngleScore + avgEarlyEdging + edgeSimilarity + avgProgressiveEdge) / 4f
        val edgingScore = if (currentAngulation != 0f || carveScoreCount > 0) {
            var score = edgingBase * 0.6f
            if (currentAngulation != 0f) score += angulationScore * 0.2f else score += edgingBase * 0.2f
            if (carveScoreCount > 0) score += avgCarveScore * 0.2f else score += edgingBase * 0.2f
            score
        } else edgingBase

        // Rotary: now includes kinetic chain order (from tri-segment)
        val rotaryBase = (parallelScore + avgTurnShape) / 2f
        val rotaryScore = if (bottomUpTurnCount > 0) {
            rotaryBase * 0.7f + (bottomUpPct / 100f * 100f) * 0.3f
        } else rotaryBase

        val pressureScoreVal = (outsideSkiPressure + avgPressureSmoothness + earlyWeightTransfer + avgWeightRelease + (avgGForce / 3f * 100f).coerceIn(0f, 100f)) / 5f

        // ── Kinetic Score: weighted composite (0–200 scale) ──
        val skiIQ = computeKineticScore(balanceScore, edgingScore, rotaryScore, pressureScoreVal)

        val loc = lastLocation
        val currentSpeed = loc?.speed ?: 0f
        val avgSpeed = if (speedSamples.isNotEmpty()) speedSamples.average().toFloat() else 0f

        _metrics = SkiMetrics(
            currentSpeed = currentSpeed,
            maxSpeed = maxSpeed,
            averageSpeed = avgSpeed,
            currentSpeedKmh = currentSpeed * 3.6f,
            maxSpeedKmh = maxSpeed * 3.6f,
            totalDistance = totalDistance,
            altitudeDrop = ((startAltitude ?: 0.0) - (loc?.altitude ?: startAltitude ?: 0.0)).toFloat()
                .coerceAtLeast(0f),
            currentAltitude = loc?.altitude ?: 0.0,
            startAltitude = startAltitude ?: 0.0,
            turnCount = turns.size,
            leftTurnCount = leftTurns,
            rightTurnCount = rightTurns,
            earlyForwardMovement = avgEarlyForward,
            centeredBalance = avgCenteredBalance,
            edgeAngle = avgEdgeAngle,
            edgeAngleScore = edgeAngleScore,
            earlyEdging = avgEarlyEdging,
            edgeSimilarity = edgeSimilarity,
            progressiveEdgeBuild = avgProgressiveEdge,
            parallelSkis = parallelScore,
            turnShape = avgTurnShape,
            outsideSkiPressure = outsideSkiPressure,
            pressureSmoothness = avgPressureSmoothness,
            earlyWeightTransfer = earlyWeightTransfer,
            transitionWeightRelease = avgWeightRelease,
            gForce = avgGForce,
            maxGForce = maxGForce,
            skiIQ = skiIQ,
            balanceScore = balanceScore,
            edgingScore = edgingScore,
            rotaryScore = rotaryScore,
            pressureScore = pressureScoreVal,
            bootFlexAngle = currentBootFlex,
            ankleAngulation = currentAngulation,
            kneeFlexion = currentKneeFlexion,
            kneeValgusMax = maxKneeValgus,
            aclRiskMax = maxAclRisk,
            kinChainBottomUpPct = bottomUpPct,
            separationAvg = avgSeparation,
            carveQualityScore = avgCarveScore,
            snowType = currentSnowType,
            jumpCount = jumpCount,
            totalAirtimeMs = totalAirtimeMs,

            // ── Asymmetry ──
            leftEdgeAngleAvg = if (leftTurnEdges.isNotEmpty()) leftTurnEdges.average().toFloat() else 0f,
            rightEdgeAngleAvg = if (rightTurnEdges.isNotEmpty()) rightTurnEdges.average().toFloat() else 0f,
            leftGForceAvg = if (leftTurnGForces.isNotEmpty()) leftTurnGForces.average().toFloat() else 0f,
            rightGForceAvg = if (rightTurnGForces.isNotEmpty()) rightTurnGForces.average().toFloat() else 0f,
            leftEarlyEdgingAvg = if (leftTurnEarlyEdging.isNotEmpty()) leftTurnEarlyEdging.average().toFloat() else 0f,
            rightEarlyEdgingAvg = if (rightTurnEarlyEdging.isNotEmpty()) rightTurnEarlyEdging.average().toFloat() else 0f,
            weakerSide = computeWeakerSide(),
            asymmetryScore = computeAsymmetryScore(),
            weakerSideMetric = computeWeakerSideMetric(),

            // ── Fatigue ──
            fatigueIndicator = computeFatigueIndicator(skiIQ),
            skiIQTrend = computeKineticScoreTrend(),
            earlyRunKineticScore = earlyRunKineticScoreAvg,
            currentWindowKineticScore = if (skiIQPerTurnWindow.size >= 5) skiIQPerTurnWindow.takeLast(5).average().toFloat() else skiIQ.toFloat(),

            // ── Turn Phase Scores ──
            phaseInitScore = turnsAvg { it.earlyEdgingScore * 0.5f + it.earlyForwardScore * 0.5f },
            phaseSteerInScore = turnsAvg { it.progressiveEdgeScore },
            phaseApexScore = turnsAvg { (it.edgeAngle / 60f * 100f).coerceIn(0f, 100f) * 0.5f + (it.gForce / 3f * 100f).coerceIn(0f, 100f) * 0.5f },
            phaseSteerOutScore = turnsAvg { it.progressiveEdgeScore * 0.5f + it.turnShapeScore * 0.5f },
            phaseTransitionScore = turnsAvg { it.weightReleaseScore * 0.5f + it.pressureSmoothnessScore * 0.5f },
            weakestPhase = computeWeakestPhase(),

            // ── Adaptive Scoring ──
            personalBestKineticScore = personalBestKineticScore,
            personalBaselineKineticScore = personalBaseline,
            improvementPct = if (personalBaseline > 0) ((skiIQ - personalBaseline) / personalBaseline * 100f) else 0f,

            runDurationMs = duration,
            isMoving = currentSpeed > 1.5f,
            currentGForce = lateralG,
            currentEdgeAngle = abs(roll),
            currentRoll = roll,
            currentPitch = pitch,
        )

        // Track Kinetic Score over time for fatigue detection
        skiIQPerTurnWindow.add(skiIQ.toFloat())
        if (turns.size <= 10) {
            earlyRunKineticScoreSum += skiIQ
            earlyRunKineticScoreCount++
            earlyRunKineticScoreAvg = earlyRunKineticScoreSum / earlyRunKineticScoreCount
        }
        // Track personal best and baseline
        if (skiIQ > personalBestKineticScore) personalBestKineticScore = skiIQ
        if (baselineTurnScores.size < 50) {
            baselineTurnScores.add(skiIQ.toFloat())
            if (baselineTurnScores.size == 50) {
                personalBaseline = baselineTurnScores.average().toFloat()
            }
        }
    }

    private fun turnsAvg(selector: (DetectedTurn) -> Float): Float {
        if (turns.isEmpty()) return 0f
        return turns.map(selector).average().toFloat()
    }

    // ── Asymmetry Computation ──

    private fun computeWeakerSide(): String {
        val leftAvg = if (leftTurnEdges.isNotEmpty()) leftTurnEdges.average() else 0.0
        val rightAvg = if (rightTurnEdges.isNotEmpty()) rightTurnEdges.average() else 0.0
        if (leftTurnEdges.isEmpty() || rightTurnEdges.isEmpty()) return ""
        return if (leftAvg < rightAvg) "LEFT" else "RIGHT"
    }

    private fun computeAsymmetryScore(): Float {
        if (leftTurnEdges.isEmpty() || rightTurnEdges.isEmpty()) return 100f
        val edgeDiff = abs(leftTurnEdges.average() - rightTurnEdges.average()).toFloat()
        val gDiff = abs(leftTurnGForces.average() - rightTurnGForces.average()).toFloat()
        val earlyDiff = abs(leftTurnEarlyEdging.average() - rightTurnEarlyEdging.average()).toFloat()
        val totalDiff = edgeDiff / 60f * 100f + gDiff / 3f * 100f + earlyDiff
        return (100f - totalDiff / 3f).coerceIn(0f, 100f)
    }

    private fun computeWeakerSideMetric(): String {
        if (leftTurnEdges.isEmpty() || rightTurnEdges.isEmpty()) return ""
        val edgeDiffPct = abs(leftTurnEdges.average() - rightTurnEdges.average()) / maxOf(leftTurnEdges.average(), rightTurnEdges.average(), 1.0) * 100
        val gDiffPct = if (leftTurnGForces.isNotEmpty() && rightTurnGForces.isNotEmpty())
            abs(leftTurnGForces.average() - rightTurnGForces.average()) / maxOf(leftTurnGForces.average(), rightTurnGForces.average(), 0.1) * 100 else 0.0
        return if (edgeDiffPct > gDiffPct) "Edge Angle" else "G-Force"
    }

    // ── Fatigue Computation ──

    private fun computeFatigueIndicator(currentKineticScore: Int): Float {
        if (earlyRunKineticScoreAvg <= 0 || turns.size < 15) return 0f
        val recentAvg = if (skiIQPerTurnWindow.size >= 5) skiIQPerTurnWindow.takeLast(5).average().toFloat() else currentKineticScore.toFloat()
        val decline = (earlyRunKineticScoreAvg - recentAvg) / earlyRunKineticScoreAvg * 100f
        return decline.coerceIn(0f, 100f)
    }

    private fun computeKineticScoreTrend(): Float {
        if (skiIQPerTurnWindow.size < 10) return 0f
        val firstHalf = skiIQPerTurnWindow.take(skiIQPerTurnWindow.size / 2).average().toFloat()
        val secondHalf = skiIQPerTurnWindow.takeLast(skiIQPerTurnWindow.size / 2).average().toFloat()
        return secondHalf - firstHalf
    }

    // ── Phase Scoring ──

    private fun computeWeakestPhase(): String {
        if (turns.isEmpty()) return ""
        val phases = mapOf(
            "Initiation" to turnsAvg { it.earlyEdgingScore * 0.5f + it.earlyForwardScore * 0.5f },
            "Steering In" to turnsAvg { it.progressiveEdgeScore },
            "Apex" to turnsAvg { (it.edgeAngle / 60f * 100f).coerceIn(0f, 100f) * 0.5f + (it.gForce / 3f * 100f).coerceIn(0f, 100f) * 0.5f },
            "Steering Out" to turnsAvg { it.progressiveEdgeScore * 0.5f + it.turnShapeScore * 0.5f },
            "Transition" to turnsAvg { it.weightReleaseScore * 0.5f + it.pressureSmoothnessScore * 0.5f },
        )
        return phases.minByOrNull { it.value }?.key ?: ""
    }

    /**
     * Kinetic Score computation.
     *
     * Weighted combination of the 4 skill category scores, mapped to 0–200 scale.
     * Weights derived from published research observation that G-force (pressure)
     * and edge angle (edging) are the strongest Kinetic Score predictors, with balance
     * and rotary as supporting factors.
     */
    private fun computeKineticScore(
        balance: Float,
        edging: Float,
        rotary: Float,
        pressure: Float,
    ): Int {
        val weighted = balance * 0.20f + edging * 0.35f + rotary * 0.15f + pressure * 0.30f
        // Map 0–100 score → 0–200 Kinetic Score
        return (weighted * 2f).toInt().coerceIn(0, 200)
    }

    companion object {
        private fun distanceBetween(
            lat1: Double, lon1: Double,
            lat2: Double, lon2: Double,
        ): Float {
            val results = FloatArray(1)
            android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results)
            return results[0]
        }
    }
}

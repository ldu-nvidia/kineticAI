package com.mycarv.app.snowboard

import com.mycarv.app.analysis.ButterworthLowPass
import com.mycarv.app.analysis.MadgwickFilter
import com.mycarv.app.sensor.ImuSample
import com.mycarv.app.sensor.LocationSample
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Snowboard-specific analysis engine.
 *
 * Uses the same sensor hardware and Madgwick AHRS as skiing,
 * but interprets the data through snowboard biomechanics:
 *   - Toeside/heelside turns instead of left/right
 *   - Spin detection from integrated yaw
 *   - Press detection from pitch angle
 *   - Switch riding detection from heading reversal
 *   - Board twist from dual-boot pitch difference
 *   - Rotational ACL risk instead of valgus
 *
 * AASI 6 core competencies:
 *   1. Fore-aft pressure (along board length)
 *   2. Lateral pressure (across board width)
 *   3. Pressure magnitude (board-surface interaction)
 *   4. Board tilt (inclination + angulation)
 *   5. Board pivot (rotation + flexion/extension)
 *   6. Board twist (torsional flex)
 */
class SnowboardAnalysisEngine(sampleRateHz: Float = 50f) {

    private val ahrs = MadgwickFilter(beta = 0.05f, samplePeriod = 1f / sampleRateHz)
    private val gyroFilter = ButterworthLowPass(cutoffHz = 3f, sampleRateHz = sampleRateHz)
    val feedbackGenerator = SnowboardFeedbackGenerator()

    private val turns = mutableListOf<SnowboardTurn>()
    private val tricks = mutableListOf<DetectedTrick>()
    private val speedSamples = mutableListOf<Float>()

    private var startTime = 0L
    private var lastTimestamp = 0L
    private var initialized = false
    private var calibrationSamples = 0
    private var calibrationAccelSum = floatArrayOf(0f, 0f, 0f)

    // Turn detection state
    private var prevFilteredGyroZ = 0f
    private var crossedThreshold = false
    private var inTurn = false
    private var turnStartTime = 0L
    private var turnRollSamples = mutableListOf<Float>()
    private var turnPitchSamples = mutableListOf<Float>()
    private var turnLateralGSamples = mutableListOf<Float>()
    private var turnVerticalGSamples = mutableListOf<Float>()
    private var turnGyroZSamples = mutableListOf<Float>()
    private var peakRoll = 0f
    private var peakGyroZ = 0f
    private var peakLateralG = 0f
    private var peakVerticalG = 0f

    // Trick detection state
    private var airborne = false
    private var airStartTime = 0L
    private var airYawAccum = 0f
    private var preAirPitch = 0f

    // Switch detection
    private var initialHeading = 0f
    private var headingSet = false
    private var switchSamples = 0
    private var totalSamples = 0

    // Location
    private var lastLocation: LocationSample? = null
    private var totalDistance = 0f
    private var maxSpeed = 0f
    private var startAltitude: Double? = null
    private var maxGForce = 0f

    // Extended sensors
    private var currentBootFlex = 0f
    private var currentAngulation = 0f
    private var currentKneeFlexion = 0f
    private var maxKneeRotation = 0f
    private var carveScoreSum = 0f
    private var carveScoreCount = 0
    private var currentSnowType = "Unknown"
    private var jumpCount = 0
    private var totalAirtimeMs = 0L
    private var rideIQHistory = mutableListOf<Float>()
    private var toesideEdges = mutableListOf<Float>()
    private var heelsideEdges = mutableListOf<Float>()

    private var _metrics = SnowboardMetrics()
    val metrics: SnowboardMetrics get() = _metrics

    val allTurns: List<SnowboardTurn> get() = turns.toList()
    val allTricks: List<DetectedTrick> get() = tricks.toList()

    fun reset() {
        ahrs.reset()
        gyroFilter.reset()
        turns.clear(); tricks.clear(); speedSamples.clear()
        startTime = 0L; lastTimestamp = 0L; initialized = false
        calibrationSamples = 0; calibrationAccelSum = floatArrayOf(0f, 0f, 0f)
        prevFilteredGyroZ = 0f; crossedThreshold = false; inTurn = false
        airborne = false; headingSet = false; switchSamples = 0; totalSamples = 0
        lastLocation = null; totalDistance = 0f; maxSpeed = 0f; startAltitude = null; maxGForce = 0f
        currentBootFlex = 0f; currentAngulation = 0f; currentKneeFlexion = 0f; maxKneeRotation = 0f
        carveScoreSum = 0f; carveScoreCount = 0; jumpCount = 0; totalAirtimeMs = 0L
        rideIQHistory.clear(); toesideEdges.clear(); heelsideEdges.clear()
        _metrics = SnowboardMetrics()
    }

    fun processImu(sample: ImuSample): Any? {
        if (startTime == 0L) startTime = sample.timestamp

        // Calibration
        if (!initialized) {
            calibrationAccelSum[0] += sample.accelX
            calibrationAccelSum[1] += sample.accelY
            calibrationAccelSum[2] += sample.accelZ
            calibrationSamples++
            if (calibrationSamples >= 50) {
                ahrs.initFromAccel(
                    calibrationAccelSum[0] / calibrationSamples,
                    calibrationAccelSum[1] / calibrationSamples,
                    calibrationAccelSum[2] / calibrationSamples,
                )
                initialized = true
            }
            lastTimestamp = sample.timestamp
            return null
        }

        val dt = if (lastTimestamp > 0) ((sample.timestamp - lastTimestamp) / 1000f).coerceIn(0.001f, 0.1f) else 0.02f
        lastTimestamp = sample.timestamp

        // Madgwick AHRS
        ahrs.update(sample.gyroX, sample.gyroY, sample.gyroZ, sample.accelX, sample.accelY, sample.accelZ, dt)

        val roll = ahrs.rollDeg
        val pitch = ahrs.pitchDeg
        val yaw = ahrs.yawDeg

        // Gravity removal
        val earthAccel = ahrs.rotateToEarth(sample.accelX, sample.accelY, sample.accelZ)
        val linearX = earthAccel[0]
        val linearZ = earthAccel[2] - 9.81f
        val lateralG = sqrt(linearX * linearX + earthAccel[1] * earthAccel[1]) / 9.81f

        // Switch detection: if heading flipped ~180° from initial
        totalSamples++
        if (!headingSet && totalSamples > 100) { initialHeading = yaw; headingSet = true }
        if (headingSet) {
            val headingDiff = abs(yaw - initialHeading)
            val isSwitch = headingDiff > 120f && headingDiff < 240f
            if (isSwitch) switchSamples++
        }

        // Turn detection (same gyro-Z zero-crossing as skiing)
        val filtered = gyroFilter.filter(sample.gyroZ)
        val turnResult = detectTurn(sample.timestamp, filtered, roll, pitch, earthAccel[2] / 9.81f, lateralG)

        // Trick detection
        val trickResult = detectTrick(sample.timestamp, sample.accelZ, sample.gyroY, pitch, yaw, dt)

        if (lateralG > maxGForce) maxGForce = lateralG

        updateMetrics(sample.timestamp, roll, pitch, lateralG)

        return turnResult ?: trickResult
    }

    fun processLocation(loc: LocationSample) {
        if (startAltitude == null) startAltitude = loc.altitude
        lastLocation?.let { prev ->
            val results = FloatArray(1)
            android.location.Location.distanceBetween(prev.latitude, prev.longitude, loc.latitude, loc.longitude, results)
            totalDistance += results[0]
        }
        lastLocation = loc
        if (loc.speed > maxSpeed) maxSpeed = loc.speed
        speedSamples.add(loc.speed)
    }

    fun processBootFlex(flex: Float, angulation: Float) { currentBootFlex = flex; currentAngulation = angulation }
    fun processKneeData(kneeFlexion: Float, kneeRotation: Float) {
        currentKneeFlexion = kneeFlexion
        if (abs(kneeRotation) > maxKneeRotation) maxKneeRotation = abs(kneeRotation)
    }
    fun processCarveQuality(quality: Int) {
        val score = when (quality) { 1 -> 100f; 2 -> 65f; 3 -> 30f; 4 -> 10f; else -> 50f }
        carveScoreSum += score; carveScoreCount++
    }
    fun processSnowType(type: String) { currentSnowType = type }
    fun processAirborne(isAir: Boolean, timestamp: Long) {
        if (isAir && !airborne) { airborne = true; airStartTime = timestamp; airYawAccum = 0f; jumpCount++ }
        else if (!isAir && airborne) { airborne = false; totalAirtimeMs += timestamp - airStartTime }
    }

    // ── Turn Detection ──

    private fun detectTurn(timestamp: Long, filteredGyroZ: Float, roll: Float, pitch: Float,
                           verticalG: Float, lateralG: Float): SnowboardTurn? {
        if (!crossedThreshold && abs(filteredGyroZ) > 0.5f) {
            crossedThreshold = true
            if (!inTurn) {
                inTurn = true; turnStartTime = timestamp
                turnRollSamples.clear(); turnPitchSamples.clear()
                turnLateralGSamples.clear(); turnVerticalGSamples.clear(); turnGyroZSamples.clear()
                peakRoll = 0f; peakGyroZ = filteredGyroZ; peakLateralG = 0f; peakVerticalG = 0f
            }
        }

        if (inTurn) {
            turnRollSamples.add(roll); turnPitchSamples.add(pitch)
            turnLateralGSamples.add(lateralG); turnVerticalGSamples.add(verticalG)
            turnGyroZSamples.add(filteredGyroZ)
            if (abs(roll) > abs(peakRoll)) peakRoll = roll
            if (abs(filteredGyroZ) > abs(peakGyroZ)) peakGyroZ = filteredGyroZ
            if (lateralG > peakLateralG) peakLateralG = lateralG
            if (verticalG > peakVerticalG) peakVerticalG = verticalG
        }

        val zeroCrossing = crossedThreshold && prevFilteredGyroZ != 0f &&
            (prevFilteredGyroZ > 0f && filteredGyroZ <= 0f || prevFilteredGyroZ < 0f && filteredGyroZ >= 0f)

        var completedTurn: SnowboardTurn? = null

        if (zeroCrossing && inTurn) {
            val duration = timestamp - turnStartTime
            if (duration >= 400 && turnRollSamples.size >= 5) {
                completedTurn = buildTurn(timestamp)
            }
            turnRollSamples.clear(); turnPitchSamples.clear()
            turnLateralGSamples.clear(); turnVerticalGSamples.clear(); turnGyroZSamples.clear()
            turnStartTime = timestamp; peakGyroZ = filteredGyroZ; peakRoll = 0f; peakLateralG = 0f; peakVerticalG = 0f
            crossedThreshold = abs(filteredGyroZ) > 0.25f
        }

        prevFilteredGyroZ = filteredGyroZ
        return completedTurn
    }

    private fun buildTurn(endTime: Long): SnowboardTurn {
        val n = turnRollSamples.size
        val edge = if (peakRoll > 0) BoardEdge.TOESIDE else BoardEdge.HEELSIDE
        val peakEdge = abs(peakRoll)

        // Early edging
        val threshold80 = peakEdge * 0.8f
        val sampleAt80 = turnRollSamples.indexOfFirst { abs(it) >= threshold80 }
        val earlyEdging = if (sampleAt80 >= 0 && n > 0) (100f * (1f - sampleAt80.toFloat() / n)).coerceIn(0f, 100f) else 0f

        // Progressive edge
        val p10 = (n * 0.10f).toInt().coerceIn(0, n - 1)
        val p80 = (n * 0.80f).toInt().coerceIn(0, n)
        val threshold90 = peakEdge * 0.9f
        val progressiveRange = turnRollSamples.subList(p10.coerceAtMost(n), p80.coerceAtMost(n))
        val progressiveScore = if (progressiveRange.isNotEmpty()) {
            (100f * progressiveRange.count { abs(it) >= threshold90 } / progressiveRange.size).coerceIn(0f, 100f)
        } else 0f

        // Turn shape (sinusoidal correlation)
        val turnShapeScore = computeTurnShape()

        // Pressure distribution (pitch average = front-back balance)
        val avgPitch = if (turnPitchSamples.isNotEmpty()) turnPitchSamples.average().toFloat() else 0f
        val pressureDist = (50f + avgPitch * 2f).coerceIn(0f, 100f)

        // Weight release
        val p85 = (n * 0.85f).toInt().coerceAtMost(n)
        val transG = turnVerticalGSamples.subList(p85.coerceAtMost(n), n)
        val troughG = transG.minOrNull() ?: 1f
        val weightRelease = if (peakVerticalG > 0.1f) ((peakVerticalG - troughG) / peakVerticalG * 100f).coerceIn(0f, 100f) else 0f

        // Carve quality from mic
        val carveQ = if (carveScoreCount > 0) carveScoreSum / carveScoreCount else 50f

        val turn = SnowboardTurn(
            edge = edge, startTime = turnStartTime, endTime = endTime,
            peakEdgeAngle = peakEdge, earlyEdgingScore = earlyEdging,
            progressiveEdgeScore = progressiveScore, turnShapeScore = turnShapeScore,
            gForce = peakLateralG, weightReleaseScore = weightRelease,
            carveQualityScore = carveQ, pressureDistribution = pressureDist,
        )

        turns.add(turn)
        if (edge == BoardEdge.TOESIDE) toesideEdges.add(peakEdge)
        else heelsideEdges.add(peakEdge)

        return turn
    }

    private fun computeTurnShape(): Float {
        val n = turnGyroZSamples.size
        if (n < 4) return 50f
        val ideal = FloatArray(n) { i ->
            val sign = if (peakGyroZ > 0) 1f else -1f
            sign * abs(peakGyroZ) * kotlin.math.sin(Math.PI.toFloat() * i / (n - 1))
        }
        val meanA = turnGyroZSamples.average().toFloat()
        val meanI = ideal.average().toFloat()
        var cov = 0f; var varA = 0f; var varI = 0f
        for (i in 0 until n) {
            val da = turnGyroZSamples[i] - meanA; val di = ideal[i] - meanI
            cov += da * di; varA += da * da; varI += di * di
        }
        val denom = sqrt(varA * varI)
        val corr = if (denom > 0.001f) cov / denom else 0f
        return (corr * 100f).coerceIn(0f, 100f)
    }

    // ── Trick Detection ──

    private fun detectTrick(timestamp: Long, accelZ: Float, gyroY: Float, pitch: Float,
                            yaw: Float, dt: Float): DetectedTrick? {
        if (airborne) {
            airYawAccum += gyroY * 57.2958f * dt
        }

        // Press detection (on ground): nose or tail press
        if (!airborne && abs(pitch) > 25f) {
            // This is a press — tracked in metrics but not as a "trick event"
        }

        // When landing from air
        if (!airborne && airStartTime > 0 && timestamp - airStartTime > 100) {
            val airtime = timestamp - airStartTime
            val rotation = abs(airYawAccum)
            airStartTime = 0

            if (airtime > 200) { // At least 200ms of air
                val type = when {
                    rotation > 630 -> TrickType.SPIN_720
                    rotation > 450 -> TrickType.SPIN_540
                    rotation > 270 -> TrickType.SPIN_360
                    rotation > 90 -> TrickType.SPIN_180
                    abs(preAirPitch) > 20 -> if (preAirPitch > 0) TrickType.OLLIE else TrickType.NOLLIE
                    else -> TrickType.OLLIE
                }

                val style = when {
                    accelZ / 9.81f < 3f && rotation > 0 -> 80 // Clean landing + rotation
                    accelZ / 9.81f < 2f -> 90 // Very clean landing
                    else -> 50
                }

                val trick = DetectedTrick(
                    type = type, startTime = airStartTime, endTime = timestamp,
                    airtimeMs = airtime, rotationDegrees = rotation,
                    maxHeight = 0f, peakGOnLanding = accelZ / 9.81f, style = style,
                )
                tricks.add(trick)
                return trick
            }
        }

        if (airborne) preAirPitch = pitch
        return null
    }

    // ── Metrics Aggregation ──

    private fun updateMetrics(timestamp: Long, roll: Float, pitch: Float, lateralG: Float) {
        val duration = timestamp - startTime
        val toesideCount = turns.count { it.edge == BoardEdge.TOESIDE }
        val heelsideCount = turns.count { it.edge == BoardEdge.HEELSIDE }

        val avgEdge = if (turns.isNotEmpty()) turns.map { it.peakEdgeAngle }.average().toFloat() else 0f
        val avgEarlyEdging = if (turns.isNotEmpty()) turns.map { it.earlyEdgingScore }.average().toFloat() else 0f
        val avgProgressive = if (turns.isNotEmpty()) turns.map { it.progressiveEdgeScore }.average().toFloat() else 0f
        val avgTurnShape = if (turns.isNotEmpty()) turns.map { it.turnShapeScore }.average().toFloat() else 0f
        val avgGForce = if (turns.isNotEmpty()) turns.map { it.gForce }.average().toFloat() else 0f
        val avgWeightRelease = if (turns.isNotEmpty()) turns.map { it.weightReleaseScore }.average().toFloat() else 0f
        val avgCarve = if (carveScoreCount > 0) carveScoreSum / carveScoreCount else 50f

        val toesideAvg = if (toesideEdges.isNotEmpty()) toesideEdges.average().toFloat() else 0f
        val heelsideAvg = if (heelsideEdges.isNotEmpty()) heelsideEdges.average().toFloat() else 0f
        val maxEdge = maxOf(toesideAvg, heelsideAvg, 1f)
        val edgeSymmetry = (1f - abs(toesideAvg - heelsideAvg) / maxEdge).coerceIn(0f, 1f)

        val edgeAngleScore = (avgEdge / 60f * 100f).coerceIn(0f, 100f)
        val edgingScore = (edgeAngleScore + avgEarlyEdging + edgeSymmetry * 100f + avgProgressive) / 4f
        val boardControlScore = (avgTurnShape + avgCarve) / 2f
        val pressureScore = ((avgGForce / 3f * 100f).coerceIn(0f, 100f) + avgWeightRelease) / 2f
        val balScore = if (currentBootFlex != 0f) (50f + currentBootFlex * 3f).coerceIn(0f, 100f) else 50f
        val styleScore = if (tricks.isNotEmpty()) {
            val trickAvg = tricks.map { it.style }.average().toFloat()
            val spinBonus = if (tricks.any { it.rotationDegrees > 270 }) 20f else 0f
            val switchBonus = if (totalSamples > 0) (switchSamples.toFloat() / totalSamples * 50f) else 0f
            (trickAvg + spinBonus + switchBonus).coerceIn(0f, 100f)
        } else 0f

        val rideIQ = computeRideIQ(edgingScore, boardControlScore, pressureScore, balScore, styleScore)
        rideIQHistory.add(rideIQ.toFloat())

        val loc = lastLocation
        val currentSpeed = loc?.speed ?: 0f

        _metrics = SnowboardMetrics(
            currentSpeed = currentSpeed, maxSpeed = maxSpeed,
            currentSpeedKmh = currentSpeed * 3.6f, maxSpeedKmh = maxSpeed * 3.6f,
            totalDistance = totalDistance,
            altitudeDrop = ((startAltitude ?: 0.0) - (loc?.altitude ?: startAltitude ?: 0.0)).toFloat().coerceAtLeast(0f),
            turnCount = turns.size, toesideTurnCount = toesideCount, heelsideTurnCount = heelsideCount,
            currentEdge = if (roll > 5) BoardEdge.TOESIDE else if (roll < -5) BoardEdge.HEELSIDE else BoardEdge.FLAT,
            edgeAngle = avgEdge, edgeAngleScore = edgeAngleScore, earlyEdging = avgEarlyEdging,
            edgeSymmetry = edgeSymmetry, progressiveEdgeBuild = avgProgressive,
            toesideEdgeAvg = toesideAvg, heelsideEdgeAvg = heelsideAvg,
            turnShape = avgTurnShape, pressureFrontBack = (50f + pitch * 2f).coerceIn(0f, 100f),
            carveQuality = avgCarve, gForce = avgGForce, maxGForce = maxGForce, weightRelease = avgWeightRelease,
            trickCount = tricks.size, totalAirtimeMs = totalAirtimeMs,
            bestTrickType = tricks.maxByOrNull { it.rotationDegrees }?.type ?: TrickType.NONE,
            maxSpinDegrees = tricks.maxOfOrNull { it.rotationDegrees } ?: 0f,
            trickStyleAvg = if (tricks.isNotEmpty()) tricks.map { it.style }.average().toFloat() else 0f,
            isSwitch = totalSamples > 0 && switchSamples.toFloat() / totalSamples > 0.3f,
            switchPercent = if (totalSamples > 0) switchSamples.toFloat() / totalSamples * 100f else 0f,
            centeredBalance = balScore, bootFlexAngle = currentBootFlex,
            kneeFlexion = currentKneeFlexion, ankleAngulation = currentAngulation,
            kneeRotationRisk = (maxKneeRotation * 5f).coerceIn(0f, 100f), aclRiskMax = (maxKneeRotation * 5f).coerceIn(0f, 100f),
            rideIQ = rideIQ, edgingScore = edgingScore, boardControlScore = boardControlScore,
            pressureScore = pressureScore, balanceScore = balScore, styleScore = styleScore,
            snowType = currentSnowType, carveQualityFromMic = avgCarve,
            fatigueIndicator = computeFatigue(), rideIQTrend = computeTrend(),
            asymmetryScore = edgeSymmetry * 100f,
            weakerEdge = if (toesideAvg < heelsideAvg) "Toeside" else if (heelsideAvg < toesideAvg) "Heelside" else "",
            phaseInitScore = avgEarlyEdging,
            phaseApexScore = (edgeAngleScore * 0.5f + (avgGForce / 3f * 100f).coerceIn(0f, 100f) * 0.5f),
            phaseTransitionScore = avgWeightRelease,
            weakestPhase = listOf("Initiation" to avgEarlyEdging, "Apex" to edgeAngleScore, "Transition" to avgWeightRelease).minByOrNull { it.second }?.first ?: "",
            runDurationMs = duration, isMoving = currentSpeed > 1.5f,
            currentGForce = lateralG, currentEdgeAngle = abs(roll), currentRoll = roll, currentPitch = pitch,
        )
    }

    private fun computeRideIQ(edging: Float, boardControl: Float, pressure: Float, balance: Float, style: Float): Int {
        val weighted = edging * 0.25f + boardControl * 0.20f + pressure * 0.20f + balance * 0.15f + style * 0.20f
        return (weighted * 2f).toInt().coerceIn(0, 200)
    }

    private fun computeFatigue(): Float {
        if (rideIQHistory.size < 15) return 0f
        val early = rideIQHistory.take(10).average().toFloat()
        val recent = rideIQHistory.takeLast(5).average().toFloat()
        return ((early - recent) / early * 100f).coerceIn(0f, 100f)
    }

    private fun computeTrend(): Float {
        if (rideIQHistory.size < 10) return 0f
        val first = rideIQHistory.take(rideIQHistory.size / 2).average().toFloat()
        val second = rideIQHistory.takeLast(rideIQHistory.size / 2).average().toFloat()
        return second - first
    }
}

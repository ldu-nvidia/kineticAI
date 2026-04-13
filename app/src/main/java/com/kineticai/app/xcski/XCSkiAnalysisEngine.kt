package com.kineticai.app.xcski

import com.kineticai.app.analysis.MadgwickFilter
import com.kineticai.app.sensor.ImuSample
import com.kineticai.app.sensor.LocationSample
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Cross-country skiing analysis engine.
 *
 * Fundamentally different from the alpine/snowboard engines:
 * - Detects stride CYCLES instead of turns
 * - Classifies sub-techniques (DS, DP, V1, V2, etc.)
 * - Tracks endurance metrics (cadence trends, fatigue)
 * - Scores on Glide, Power, Symmetry, Technique (not Edging/Rotary)
 *
 * Sensor pipeline:
 *   Phone IMU 50Hz -> Madgwick AHRS -> StrideCycleDetector -> SubTechniqueClassifier
 *   GPS 1Hz -> terrain grade + distance
 *   Barometer -> altitude
 */
class XCSkiAnalysisEngine(sampleRateHz: Float = 50f) {

    private val ahrs = MadgwickFilter(beta = 0.04f, samplePeriod = 1f / sampleRateHz)
    val cycleDetector = StrideCycleDetector(sampleRateHz)
    val classifier = SubTechniqueClassifier()
    val feedbackGenerator = XCSkiFeedbackGenerator()

    private val strides = mutableListOf<StrideCycle>()

    private var startTime = 0L
    private var lastLocationTime = 0L
    private var lastLatitude = 0.0
    private var lastLongitude = 0.0
    private var lastAltitude = 0f
    private var currentSpeedKmh = 0f
    private var currentGradePercent = 0f
    private var currentAltitude = 0f

    private var totalDistanceM = 0f
    private var totalClimbM = 0f
    private var totalDescentM = 0f
    private var maxSpeedKmh = 0f
    private var speedSum = 0f
    private var speedCount = 0

    // Scoring accumulators
    private var glideSum = 0f
    private var powerVarianceSum = 0f
    private var symmetrySum = 0f
    private var techniqueCorrectCount = 0
    private var recentCycleRates = mutableListOf<Float>()

    // Technique distribution tracking
    private val techniqueDurations = mutableMapOf<SubTechnique, Long>()

    // Fatigue: compare first-quarter cycle rate to current
    private val earlySessionCycleRates = mutableListOf<Float>()
    private var earlySessionLocked = false

    // Calibration
    private var isCalibrated = false
    private var calibrationSamples = 0
    private val calibrationCount = 50

    fun processImu(sample: ImuSample) {
        if (startTime == 0L) startTime = sample.timestamp

        // Calibration phase: hold still for ~1 second
        if (!isCalibrated) {
            ahrs.update(sample.gyroX, sample.gyroY, sample.gyroZ, sample.accelX, sample.accelY, sample.accelZ)
            calibrationSamples++
            if (calibrationSamples >= calibrationCount) {
                isCalibrated = true
            }
            return
        }

        ahrs.update(sample.gyroX, sample.gyroY, sample.gyroZ, sample.accelX, sample.accelY, sample.accelZ)
        val rollDeg = ahrs.rollDeg
        val pitchDeg = ahrs.pitchDeg

        val cycleBoundary = cycleDetector.process(
            sample.accelX, sample.accelY, sample.accelZ,
            rollDeg, pitchDeg,
            sample.timestamp
        )

        if (cycleBoundary != null) {
            onCycleDetected(cycleBoundary, sample.timestamp)
        }
    }

    fun processLocation(loc: LocationSample) {
        if (lastLocationTime > 0) {
            val dt = (loc.timestamp - lastLocationTime) / 1000f
            if (dt > 0) {
                currentSpeedKmh = loc.speed * 3.6f
                maxSpeedKmh = max(maxSpeedKmh, currentSpeedKmh)
                speedSum += currentSpeedKmh
                speedCount++

                val dist = haversineM(lastLatitude, lastLongitude, loc.latitude, loc.longitude)
                totalDistanceM += dist.toFloat()

                val altDiff = loc.altitude.toFloat() - lastAltitude
                if (altDiff > 0.5f) totalClimbM += altDiff
                else if (altDiff < -0.5f) totalDescentM += abs(altDiff)

                if (dt > 0 && dist > 0.5) {
                    currentGradePercent = (altDiff / dist.toFloat()) * 100f
                }
            }
        }

        lastLocationTime = loc.timestamp
        lastLatitude = loc.latitude
        lastLongitude = loc.longitude
        lastAltitude = loc.altitude.toFloat()
        currentAltitude = lastAltitude
    }

    private fun onCycleDetected(cycle: CycleBoundary, timestamp: Long) {
        val features = CycleFeatures(
            lateralSwayAmplitude = cycle.lateralSwayDeg,
            lateralSwaySymmetry = cycle.lateralSymmetry,
            pitchOscillationAmplitude = cycle.pitchOscillationDeg,
            cycleDurationMs = cycle.durationMs,
            speedKmh = currentSpeedKmh,
            gradePercent = currentGradePercent,
        )

        val technique = classifier.classify(features)
        val terrainGrade = TerrainGrade.fromGradePercent(currentGradePercent)
        val cycleRate = 60000f / cycle.durationMs
        val cycleLength = if (cycleRate > 0) (currentSpeedKmh / 3.6f) / (cycleRate / 60f) else 0f

        val stride = StrideCycle(
            startTime = cycle.cycleStartTime,
            endTime = cycle.cycleEndTime,
            technique = technique,
            terrainGrade = terrainGrade,
            gradePercent = currentGradePercent,
            cycleDurationMs = cycle.durationMs,
            cycleRatePerMin = cycleRate,
            cycleLengthMeters = cycleLength,
            glideTimeRatio = cycle.glideTimeRatio,
            polingTimeRatio = 1f - cycle.glideTimeRatio - 0.15f,
            recoveryTimeRatio = 0.15f,
            lateralSwayDeg = cycle.lateralSwayDeg,
            lateralSymmetry = cycle.lateralSymmetry,
            pitchOscillationDeg = cycle.pitchOscillationDeg,
            peakAccelG = cycle.peakAccelG,
            speedKmh = currentSpeedKmh,
            altitudeM = currentAltitude,
        )

        strides.add(stride)

        // Track technique duration
        techniqueDurations[technique] = (techniqueDurations[technique] ?: 0L) + cycle.durationMs

        // Accumulate scoring data
        glideSum += cycle.glideTimeRatio
        symmetrySum += cycle.lateralSymmetry
        recentCycleRates.add(cycleRate)
        if (recentCycleRates.size > 20) recentCycleRates.removeAt(0)

        if (isCorrectTechniqueForTerrain(technique, terrainGrade)) techniqueCorrectCount++

        // Early session baseline for fatigue
        if (!earlySessionLocked && strides.size <= 30) {
            earlySessionCycleRates.add(cycleRate)
        }
        if (!earlySessionLocked && strides.size == 30) {
            earlySessionLocked = true
        }
    }

    fun getMetrics(): XCSkiMetrics {
        val n = strides.size
        if (n == 0) return XCSkiMetrics(
            currentTechnique = SubTechnique.STANDING,
            currentSpeedKmh = currentSpeedKmh,
            currentTerrainGrade = TerrainGrade.fromGradePercent(currentGradePercent),
        )

        val elapsed = if (strides.isNotEmpty()) strides.last().endTime - startTime else 0L

        val avgCycleRate = strides.map { it.cycleRatePerMin }.average().toFloat()
        val avgCycleLength = strides.map { it.cycleLengthMeters }.average().toFloat()
        val avgGlideRatio = strides.map { it.glideTimeRatio }.average().toFloat()
        val avgSymmetry = strides.map { it.lateralSymmetry }.average().toFloat()

        // Technique distribution as percentages
        val totalDuration = techniqueDurations.values.sum().toFloat()
        val distribution = if (totalDuration > 0) {
            techniqueDurations.mapValues { (it.value / totalDuration) * 100f }
        } else emptyMap()

        // Speed by technique
        val speedByTech = strides.groupBy { it.technique }
            .mapValues { entry -> entry.value.map { it.speedKmh }.average().toFloat() }

        // --- Scoring (0-100 per category, composite 0-200) ---
        val glideScore = computeGlideScore(avgGlideRatio, avgCycleLength)
        val powerScore = computePowerScore()
        val symmetryScore = computeSymmetryScore(avgSymmetry)
        val techniqueScore = computeTechniqueScore()

        val strideScore = (glideScore * 0.30f + powerScore * 0.25f +
                symmetryScore * 0.20f + techniqueScore * 0.25f) * 2f // scale to 0-200

        // Fatigue
        val fatigueFactor = computeFatigue()

        // Cycle rate trend (positive = speeding up, negative = slowing down)
        val trend = if (recentCycleRates.size >= 10) {
            val first = recentCycleRates.take(5).average().toFloat()
            val last = recentCycleRates.takeLast(5).average().toFloat()
            last - first
        } else 0f

        val current = strides.lastOrNull()

        return XCSkiMetrics(
            totalStrides = n,
            totalDistanceM = totalDistanceM,
            totalClimbM = totalClimbM,
            totalDescentM = totalDescentM,
            elapsedMs = elapsed,
            avgCycleRate = avgCycleRate,
            avgCycleLength = avgCycleLength,
            avgGlideRatio = avgGlideRatio,
            avgLateralSymmetry = avgSymmetry,
            avgSpeedKmh = if (speedCount > 0) speedSum / speedCount else 0f,
            maxSpeedKmh = maxSpeedKmh,
            currentTechnique = current?.technique ?: SubTechnique.UNKNOWN,
            currentCycleRate = current?.cycleRatePerMin ?: 0f,
            currentSpeedKmh = currentSpeedKmh,
            currentTerrainGrade = TerrainGrade.fromGradePercent(currentGradePercent),
            techniqueDistribution = distribution,
            speedByTechnique = speedByTech,
            glideScore = glideScore,
            powerScore = powerScore,
            symmetryScore = symmetryScore,
            techniqueScore = techniqueScore,
            strideScore = min(200f, strideScore),
            cycleRateTrend = trend,
            fatigueFactor = fatigueFactor,
            sessionDurationMs = elapsed,
        )
    }

    // Glide: higher ratio = better, longer cycle length = more efficient
    private fun computeGlideScore(avgGlide: Float, avgLength: Float): Float {
        val glideComponent = (avgGlide.coerceIn(0.3f, 0.7f) - 0.3f) / 0.4f * 60f
        val lengthComponent = (avgLength.coerceIn(2f, 8f) - 2f) / 6f * 40f
        return (glideComponent + lengthComponent).coerceIn(0f, 100f)
    }

    // Power: consistent cycle rate = good power application
    private fun computePowerScore(): Float {
        if (recentCycleRates.size < 5) return 50f
        val mean = recentCycleRates.average().toFloat()
        val variance = recentCycleRates.map { (it - mean) * (it - mean) }.average().toFloat()
        val cv = if (mean > 0) sqrt(variance) / mean else 1f
        return ((1f - cv.coerceIn(0f, 0.3f)) / 0.3f * 100f).coerceIn(0f, 100f)
    }

    // Symmetry: L/R balance
    private fun computeSymmetryScore(avgSym: Float): Float {
        return (avgSym * 100f).coerceIn(0f, 100f)
    }

    // Technique: correct sub-technique for terrain
    private fun computeTechniqueScore(): Float {
        val n = strides.size
        if (n == 0) return 50f
        return (techniqueCorrectCount.toFloat() / n * 100f).coerceIn(0f, 100f)
    }

    private fun isCorrectTechniqueForTerrain(tech: SubTechnique, terrain: TerrainGrade): Boolean {
        return when (terrain) {
            TerrainGrade.STEEP_DOWNHILL -> tech in listOf(
                SubTechnique.CLASSIC_TUCK, SubTechnique.SKATE_TUCK
            )
            TerrainGrade.MODERATE_DOWNHILL -> tech in listOf(
                SubTechnique.DOUBLE_POLING, SubTechnique.CLASSIC_TUCK,
                SubTechnique.SKATE_TUCK, SubTechnique.V2_ONE_SKATE, SubTechnique.FREE_SKATE
            )
            TerrainGrade.FLAT -> tech in listOf(
                SubTechnique.DOUBLE_POLING, SubTechnique.DIAGONAL_STRIDE,
                SubTechnique.V2_ONE_SKATE, SubTechnique.V2_ALTERNATE, SubTechnique.FREE_SKATE
            )
            TerrainGrade.MODERATE_UPHILL -> tech in listOf(
                SubTechnique.DIAGONAL_STRIDE, SubTechnique.KICK_DOUBLE_POLE,
                SubTechnique.V1_OFFSET, SubTechnique.V2_ONE_SKATE
            )
            TerrainGrade.STEEP_UPHILL -> tech in listOf(
                SubTechnique.HERRINGBONE, SubTechnique.DIAGONAL_STRIDE,
                SubTechnique.V1_OFFSET
            )
        }
    }

    private fun computeFatigue(): Float {
        if (!earlySessionLocked || recentCycleRates.size < 5) return 0f
        val earlyAvg = earlySessionCycleRates.average().toFloat()
        val currentAvg = recentCycleRates.takeLast(10).average().toFloat()
        if (earlyAvg <= 0f) return 0f
        val decline = (earlyAvg - currentAvg) / earlyAvg
        return (decline * 100f).coerceIn(-20f, 50f)
    }

    private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    fun reset() {
        strides.clear()
        cycleDetector.reset()
        techniqueDurations.clear()
        recentCycleRates.clear()
        earlySessionCycleRates.clear()
        earlySessionLocked = false
        startTime = 0L
        totalDistanceM = 0f
        totalClimbM = 0f
        totalDescentM = 0f
        maxSpeedKmh = 0f
        speedSum = 0f
        speedCount = 0
        glideSum = 0f
        symmetrySum = 0f
        techniqueCorrectCount = 0
        isCalibrated = false
        calibrationSamples = 0
    }
}

package com.kineticai.app.xcski

import com.kineticai.app.analysis.ButterworthLowPass
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Detects individual stride cycles in cross-country skiing from accelerometer data.
 *
 * XC skiing produces a rhythmic oscillation in accelerometer magnitude as the skier
 * pushes and glides. Each stride cycle corresponds to one peak in the filtered
 * accel magnitude signal.
 *
 * Pipeline: raw accel magnitude -> Butterworth LPF (4 Hz cutoff) -> peak detection
 *
 * Cycle rates for XC skiing are typically 40-80 cycles/min (0.67-1.33 Hz),
 * so a 4 Hz cutoff preserves the stride rhythm while removing high-freq noise.
 */
class StrideCycleDetector(sampleRateHz: Float = 50f) {

    private val lpf = ButterworthLowPass(cutoffHz = 4f, sampleRateHz = sampleRateHz)

    private var prevFiltered = 0f
    private var prevPrevFiltered = 0f
    private var lastPeakTime = 0L
    private var lastPeakValue = 0f

    private var sampleCount = 0
    private val warmupSamples = 20

    // Adaptive threshold: running mean + fraction of running variance
    private var runningMean = 9.81f
    private var runningVar = 0.5f
    private val adaptAlpha = 0.005f

    // Constraints
    private val minCycleIntervalMs = 400L   // max 150 cycles/min
    private val maxCycleIntervalMs = 3000L  // min 20 cycles/min

    // Rolling buffer for glide ratio estimation within a cycle
    private val cycleAccelBuffer = mutableListOf<Float>()
    private var cycleRollBuffer = mutableListOf<Float>()
    private var cycleStartTime = 0L

    /**
     * Feed one accelerometer sample. Returns a detected cycle boundary if a peak
     * was found, or null otherwise.
     *
     * @param ax accel X (m/s^2)
     * @param ay accel Y (m/s^2)
     * @param az accel Z (m/s^2)
     * @param rollDeg current roll angle from AHRS (for lateral sway)
     * @param pitchDeg current pitch angle from AHRS
     * @param timestampMs sample timestamp
     * @return CycleBoundary if a new cycle was detected
     */
    fun process(
        ax: Float, ay: Float, az: Float,
        rollDeg: Float, pitchDeg: Float,
        timestampMs: Long
    ): CycleBoundary? {
        val magnitude = sqrt(ax * ax + ay * ay + az * az)
        val filtered = lpf.filter(magnitude)

        sampleCount++
        cycleAccelBuffer.add(filtered)
        cycleRollBuffer.add(rollDeg)

        // Update adaptive threshold
        runningMean += adaptAlpha * (filtered - runningMean)
        val diff = filtered - runningMean
        runningVar += adaptAlpha * (diff * diff - runningVar)

        val threshold = runningMean + sqrt(runningVar) * 0.6f

        var result: CycleBoundary? = null

        if (sampleCount > warmupSamples) {
            // Peak detection: prev sample was higher than both neighbors and above threshold
            val isPeak = prevFiltered > prevPrevFiltered &&
                    prevFiltered > filtered &&
                    prevFiltered > threshold

            if (isPeak) {
                val elapsed = timestampMs - lastPeakTime

                if (lastPeakTime > 0 && elapsed in minCycleIntervalMs..maxCycleIntervalMs) {
                    val cycleStats = extractCycleStats()
                    result = CycleBoundary(
                        cycleStartTime = cycleStartTime,
                        cycleEndTime = timestampMs,
                        durationMs = elapsed,
                        peakAccelG = (prevFiltered / 9.81f),
                        lateralSwayDeg = cycleStats.lateralSwayDeg,
                        lateralSymmetry = cycleStats.lateralSymmetry,
                        pitchOscillationDeg = cycleStats.pitchOscillationDeg,
                        glideTimeRatio = cycleStats.glideTimeRatio,
                    )
                }

                lastPeakTime = timestampMs
                lastPeakValue = prevFiltered
                cycleStartTime = timestampMs
                cycleAccelBuffer.clear()
                cycleRollBuffer.clear()
            }
        }

        prevPrevFiltered = prevFiltered
        prevFiltered = filtered

        return result
    }

    private fun extractCycleStats(): CycleStats {
        if (cycleAccelBuffer.size < 4 || cycleRollBuffer.size < 4) {
            return CycleStats(0f, 1f, 0f, 0.5f)
        }

        // Lateral sway: peak-to-peak roll
        val maxRoll = cycleRollBuffer.max()
        val minRoll = cycleRollBuffer.min()
        val lateralSway = abs(maxRoll - minRoll)

        // Lateral symmetry: ratio of positive vs negative roll peaks
        val positivePeak = abs(maxRoll)
        val negativePeak = abs(minRoll)
        val symmetry = if (positivePeak + negativePeak > 0.1f) {
            val smaller = minOf(positivePeak, negativePeak)
            val larger = maxOf(positivePeak, negativePeak)
            smaller / larger
        } else 1f

        // Pitch oscillation from accel signal variation (proxy)
        val accelRange = cycleAccelBuffer.max() - cycleAccelBuffer.min()
        val pitchOsc = (accelRange / 9.81f) * 30f // rough degrees estimate

        // Glide time ratio: fraction of cycle where accel is below mean (coasting)
        val cycleMean = cycleAccelBuffer.average().toFloat()
        val glideCount = cycleAccelBuffer.count { it < cycleMean }
        val glideRatio = glideCount.toFloat() / cycleAccelBuffer.size

        return CycleStats(lateralSway, symmetry, pitchOsc, glideRatio)
    }

    fun reset() {
        prevFiltered = 0f
        prevPrevFiltered = 0f
        lastPeakTime = 0L
        sampleCount = 0
        runningMean = 9.81f
        runningVar = 0.5f
        cycleAccelBuffer.clear()
        cycleRollBuffer.clear()
    }
}

data class CycleBoundary(
    val cycleStartTime: Long,
    val cycleEndTime: Long,
    val durationMs: Long,
    val peakAccelG: Float,
    val lateralSwayDeg: Float,
    val lateralSymmetry: Float,
    val pitchOscillationDeg: Float,
    val glideTimeRatio: Float,
)

private data class CycleStats(
    val lateralSwayDeg: Float,
    val lateralSymmetry: Float,
    val pitchOscillationDeg: Float,
    val glideTimeRatio: Float,
)

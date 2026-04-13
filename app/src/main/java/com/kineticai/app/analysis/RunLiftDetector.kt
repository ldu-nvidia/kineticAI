package com.kineticai.app.analysis

import com.kineticai.app.sensor.LocationSample

/**
 * State machine for automatic detection of ski runs vs chairlift rides.
 *
 * Uses barometric altitude and GPS speed to classify the skier's state:
 *   IDLE → ASCENDING (lift) → AT_TOP → DESCENDING (skiing) → AT_BOTTOM → IDLE
 *
 * Barometer is the primary signal (low power, fast, ~10cm resolution).
 * GPS speed is secondary for movement detection.
 *
 * Power optimization: during ASCENDING, IMU can be throttled to save battery.
 */
class RunLiftDetector {

    enum class State {
        IDLE,
        ASCENDING,   // on chairlift or hiking
        AT_TOP,      // reached summit, waiting to start
        DESCENDING,  // actively skiing
        AT_BOTTOM,   // stopped at base
    }

    var state: State = State.IDLE
        private set

    private var altitudeHistory = mutableListOf<Pair<Long, Double>>() // (timestamp, altitude)
    private var lastSpeed = 0f
    private var stoppedSince = 0L
    private var lastStateChange = 0L
    private var runStartAltitude = 0.0
    private var peakAltitude = 0.0

    // Callbacks
    var onRunStarted: (() -> Unit)? = null
    var onRunEnded: (() -> Unit)? = null
    var onLiftStarted: (() -> Unit)? = null
    var onLiftEnded: (() -> Unit)? = null

    fun processLocation(loc: LocationSample) {
        lastSpeed = loc.speed
        altitudeHistory.add(loc.timestamp to loc.altitude)

        // Keep only last 60 seconds of altitude data
        val cutoff = loc.timestamp - 60_000
        altitudeHistory.removeAll { it.first < cutoff }

        if (loc.altitude > peakAltitude) peakAltitude = loc.altitude

        val altitudeChange30s = computeAltitudeChange(loc.timestamp, 30_000)
        val altitudeChange10s = computeAltitudeChange(loc.timestamp, 10_000)

        when (state) {
            State.IDLE -> {
                if (altitudeChange30s > 5.0 && loc.speed < 3f) {
                    // Rising slowly → chairlift
                    transition(State.ASCENDING)
                    onLiftStarted?.invoke()
                } else if (loc.speed > 3f && altitudeChange10s < -2.0) {
                    // Moving fast and descending → already skiing
                    transition(State.DESCENDING)
                    runStartAltitude = altitudeHistory.firstOrNull()?.second ?: loc.altitude
                    onRunStarted?.invoke()
                }
            }

            State.ASCENDING -> {
                if (altitudeChange10s < 1.0 && loc.speed < 1f) {
                    // Stopped climbing → at the top
                    transition(State.AT_TOP)
                    peakAltitude = loc.altitude
                    onLiftEnded?.invoke()
                } else if (loc.speed > 5f && altitudeChange10s < -3.0) {
                    // Suddenly descending fast → abort lift, started skiing
                    transition(State.DESCENDING)
                    runStartAltitude = peakAltitude
                    onLiftEnded?.invoke()
                    onRunStarted?.invoke()
                }
            }

            State.AT_TOP -> {
                if (loc.speed > 3f && altitudeChange10s < -2.0) {
                    transition(State.DESCENDING)
                    runStartAltitude = loc.altitude
                    onRunStarted?.invoke()
                } else if (timeSinceStateChange(loc.timestamp) > 120_000) {
                    // Idle at top for >2 minutes → back to IDLE
                    transition(State.IDLE)
                }
            }

            State.DESCENDING -> {
                if (loc.speed < 1f) {
                    if (stoppedSince == 0L) stoppedSince = loc.timestamp
                    if (loc.timestamp - stoppedSince > 15_000) {
                        // Stopped for >15 seconds → run ended
                        transition(State.AT_BOTTOM)
                        onRunEnded?.invoke()
                        stoppedSince = 0L
                    }
                } else {
                    stoppedSince = 0L
                }
            }

            State.AT_BOTTOM -> {
                if (altitudeChange30s > 5.0 && loc.speed < 3f) {
                    transition(State.ASCENDING)
                    onLiftStarted?.invoke()
                } else if (loc.speed > 3f && altitudeChange10s < -2.0) {
                    // Another run without taking the lift
                    transition(State.DESCENDING)
                    runStartAltitude = loc.altitude
                    onRunStarted?.invoke()
                } else if (timeSinceStateChange(loc.timestamp) > 60_000) {
                    transition(State.IDLE)
                }
            }
        }
    }

    fun processBarometer(timestamp: Long, pressureHPa: Float) {
        // Convert pressure to approximate altitude: ΔZ ≈ -8.3 * ΔP (near sea level)
        // For relative changes, we track the raw pressure and compute altitude from it
        val altitudeM = (1013.25 - pressureHPa) * 8.3
        altitudeHistory.add(timestamp to altitudeM)

        val cutoff = timestamp - 60_000
        altitudeHistory.removeAll { it.first < cutoff }
    }

    val isSkiing: Boolean get() = state == State.DESCENDING
    val isOnLift: Boolean get() = state == State.ASCENDING
    val verticalDrop: Double get() = (runStartAltitude - (altitudeHistory.lastOrNull()?.second ?: runStartAltitude)).coerceAtLeast(0.0)

    fun reset() {
        state = State.IDLE
        altitudeHistory.clear()
        lastSpeed = 0f
        stoppedSince = 0L
        lastStateChange = 0L
        runStartAltitude = 0.0
        peakAltitude = 0.0
    }

    private fun computeAltitudeChange(now: Long, windowMs: Long): Double {
        val cutoff = now - windowMs
        val early = altitudeHistory.firstOrNull { it.first >= cutoff } ?: return 0.0
        val late = altitudeHistory.lastOrNull() ?: return 0.0
        return late.second - early.second
    }

    private fun timeSinceStateChange(now: Long): Long = now - lastStateChange

    private fun transition(newState: State) {
        state = newState
        lastStateChange = System.currentTimeMillis()
    }
}

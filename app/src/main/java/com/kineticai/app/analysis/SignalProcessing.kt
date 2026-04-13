package com.kineticai.app.analysis

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Second-order Butterworth low-pass filter (IIR).
 * Used to smooth gyroscope signals before turn detection.
 *
 * Research validated 3 Hz cutoff for gyro-Z turn detection in alpine skiing
 * (Martínez et al., Sensors 19(4), 2019).
 */
class ButterworthLowPass(cutoffHz: Float, sampleRateHz: Float) {

    private val b0: Float
    private val b1: Float
    private val b2: Float
    private val a1: Float
    private val a2: Float

    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f

    init {
        val omega = 2f * PI.toFloat() * cutoffHz / sampleRateHz
        val cosOmega = cos(omega)
        val sinOmega = sin(omega)
        val alpha = sinOmega / (2f * sqrt(2f)) // Q = 1/√2 for Butterworth

        val a0 = 1f + alpha
        b0 = ((1f - cosOmega) / 2f) / a0
        b1 = (1f - cosOmega) / a0
        b2 = b0
        a1 = (-2f * cosOmega) / a0
        a2 = (1f - alpha) / a0
    }

    fun filter(x: Float): Float {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x
        y2 = y1; y1 = y
        return y
    }

    fun reset() {
        x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f
    }
}

/**
 * Simple moving average for quick smoothing of noisy signals.
 */
class MovingAverage(private val windowSize: Int) {
    private val buffer = FloatArray(windowSize)
    private var index = 0
    private var count = 0
    private var sum = 0f

    fun add(value: Float): Float {
        if (count >= windowSize) {
            sum -= buffer[index]
        } else {
            count++
        }
        buffer[index] = value
        sum += value
        index = (index + 1) % windowSize
        return sum / count
    }

    fun reset() {
        buffer.fill(0f); index = 0; count = 0; sum = 0f
    }
}

/**
 * Circular buffer for accumulating per-turn time-series data.
 */
class RingBuffer(private val capacity: Int) {
    private val data = FloatArray(capacity)
    private var head = 0
    var size = 0; private set

    fun add(value: Float) {
        data[head] = value
        head = (head + 1) % capacity
        if (size < capacity) size++
    }

    fun toList(): List<Float> {
        if (size == 0) return emptyList()
        val result = ArrayList<Float>(size)
        val start = if (size < capacity) 0 else head
        for (i in 0 until size) {
            result.add(data[(start + i) % capacity])
        }
        return result
    }

    fun clear() {
        head = 0; size = 0
    }
}

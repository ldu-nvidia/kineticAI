package com.mycarv.app.analysis

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Madgwick AHRS orientation filter for 6-axis IMU (accelerometer + gyroscope).
 *
 * Maintains a quaternion representing the sensor frame orientation relative to
 * the Earth frame. Uses gradient-descent optimization to fuse gyroscope
 * integration with accelerometer gravity reference.
 *
 * Reference: Madgwick, S. "An Efficient Orientation Filter for Inertial and
 * Inertial/Magnetic Measurement Units." University of Bristol, 2010.
 *
 * Computational cost: 109 scalar arithmetic operations per update.
 */
class MadgwickFilter(
    private var beta: Float = 0.05f,
    private val samplePeriod: Float = 0.02f, // 50 Hz default
) {
    var q0 = 1f; private set
    var q1 = 0f; private set
    var q2 = 0f; private set
    var q3 = 0f; private set

    fun reset() {
        q0 = 1f; q1 = 0f; q2 = 0f; q3 = 0f
    }

    /**
     * Initialize quaternion from a static accelerometer reading (device at rest).
     * Aligns the filter so that the measured gravity vector maps to Earth-frame [0, 0, 1].
     */
    fun initFromAccel(ax: Float, ay: Float, az: Float) {
        val norm = sqrt(ax * ax + ay * ay + az * az)
        if (norm < 0.01f) return
        val nx = ax / norm
        val ny = ay / norm
        val nz = az / norm

        // Shortest rotation from measured gravity to [0, 0, 1]
        val dot = nz // dot([nx,ny,nz], [0,0,1])
        if (dot < -0.999f) {
            q0 = 0f; q1 = 1f; q2 = 0f; q3 = 0f
            return
        }
        // cross = [ny, -nx, 0], angle via half-angle formula
        q0 = 1f + dot
        q1 = ny
        q2 = -nx
        q3 = 0f
        val qNorm = sqrt(q0 * q0 + q1 * q1 + q2 * q2 + q3 * q3)
        q0 /= qNorm; q1 /= qNorm; q2 /= qNorm; q3 /= qNorm
    }

    fun setBeta(newBeta: Float) {
        beta = newBeta
    }

    /**
     * Core filter update. Call once per IMU sample.
     *
     * @param gx gyroscope x (rad/s)
     * @param gy gyroscope y (rad/s)
     * @param gz gyroscope z (rad/s)
     * @param ax accelerometer x (m/s²)
     * @param ay accelerometer y (m/s²)
     * @param az accelerometer z (m/s²)
     * @param dt time step in seconds (overrides samplePeriod if provided)
     */
    fun update(gx: Float, gy: Float, gz: Float, ax: Float, ay: Float, az: Float, dt: Float = samplePeriod) {
        var _q0 = q0; var _q1 = q1; var _q2 = q2; var _q3 = q3

        // Rate of change from gyroscope (quaternion derivative)
        var qDot0 = 0.5f * (-_q1 * gx - _q2 * gy - _q3 * gz)
        var qDot1 = 0.5f * (_q0 * gx + _q2 * gz - _q3 * gy)
        var qDot2 = 0.5f * (_q0 * gy - _q1 * gz + _q3 * gx)
        var qDot3 = 0.5f * (_q0 * gz + _q1 * gy - _q2 * gx)

        // Accelerometer correction (only if non-zero)
        val aNorm = sqrt(ax * ax + ay * ay + az * az)
        if (aNorm > 0.01f) {
            val recipNorm = 1f / aNorm
            val nax = ax * recipNorm
            val nay = ay * recipNorm
            val naz = az * recipNorm

            // Gradient descent step: objective function f and Jacobian J
            // f = q* ⊗ [0,0,0,g] ⊗ q - [0, ax, ay, az]
            val f0 = 2f * (_q1 * _q3 - _q0 * _q2) - nax
            val f1 = 2f * (_q0 * _q1 + _q2 * _q3) - nay
            val f2 = 2f * (0.5f - _q1 * _q1 - _q2 * _q2) - naz

            // Compute gradient (Jᵀf)
            var s0 = -2f * _q2 * f0 + 2f * _q1 * f1
            var s1 = 2f * _q3 * f0 + 2f * _q0 * f1 - 4f * _q1 * f2
            var s2 = -2f * _q0 * f0 + 2f * _q3 * f1 - 4f * _q2 * f2
            var s3 = 2f * _q1 * f0 + 2f * _q2 * f1

            // Normalize gradient
            val sNorm = sqrt(s0 * s0 + s1 * s1 + s2 * s2 + s3 * s3)
            if (sNorm > 0.0001f) {
                val recipSNorm = 1f / sNorm
                s0 *= recipSNorm; s1 *= recipSNorm; s2 *= recipSNorm; s3 *= recipSNorm

                qDot0 -= beta * s0
                qDot1 -= beta * s1
                qDot2 -= beta * s2
                qDot3 -= beta * s3
            }
        }

        // Integrate
        _q0 += qDot0 * dt
        _q1 += qDot1 * dt
        _q2 += qDot2 * dt
        _q3 += qDot3 * dt

        // Normalize quaternion
        val qNorm = sqrt(_q0 * _q0 + _q1 * _q1 + _q2 * _q2 + _q3 * _q3)
        if (qNorm > 0.0001f) {
            val recipQNorm = 1f / qNorm
            q0 = _q0 * recipQNorm
            q1 = _q1 * recipQNorm
            q2 = _q2 * recipQNorm
            q3 = _q3 * recipQNorm
        }
    }

    /** Roll angle in degrees (maps to edge angle). */
    val rollDeg: Float
        get() = Math.toDegrees(
            atan2(2f * (q0 * q1 + q2 * q3), 1f - 2f * (q1 * q1 + q2 * q2)).toDouble()
        ).toFloat()

    /** Pitch angle in degrees (maps to fore-aft lean). */
    val pitchDeg: Float
        get() {
            val sinp = 2f * (q0 * q2 - q3 * q1)
            return Math.toDegrees(
                if (sinp >= 1f) (Math.PI / 2).toFloat().toDouble()
                else if (sinp <= -1f) (-Math.PI / 2).toFloat().toDouble()
                else asin(sinp).toDouble()
            ).toFloat()
        }

    /** Yaw angle in degrees (maps to heading). */
    val yawDeg: Float
        get() = Math.toDegrees(
            atan2(2f * (q0 * q3 + q1 * q2), 1f - 2f * (q2 * q2 + q3 * q3)).toDouble()
        ).toFloat()

    /**
     * Rotate a body-frame vector into Earth frame using current orientation.
     * Used for gravity removal: a_linear = R(q) × a_body − [0, 0, g]
     */
    fun rotateToEarth(x: Float, y: Float, z: Float): FloatArray {
        val ex = (1f - 2f * (q2 * q2 + q3 * q3)) * x +
                 2f * (q1 * q2 - q0 * q3) * y +
                 2f * (q1 * q3 + q0 * q2) * z
        val ey = 2f * (q1 * q2 + q0 * q3) * x +
                 (1f - 2f * (q1 * q1 + q3 * q3)) * y +
                 2f * (q2 * q3 - q0 * q1) * z
        val ez = 2f * (q1 * q3 - q0 * q2) * x +
                 2f * (q2 * q3 + q0 * q1) * y +
                 (1f - 2f * (q1 * q1 + q2 * q2)) * z
        return floatArrayOf(ex, ey, ez)
    }
}

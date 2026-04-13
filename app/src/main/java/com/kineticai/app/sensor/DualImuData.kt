package com.kineticai.app.sensor

/**
 * Parsed dual IMU metrics from boot sensor over BLE.
 * 10 bytes packed from boot flex, angulation, ankle steering,
 * vibration, absorption, heading, distance, and flags.
 */
data class DualImuData(
    val bootFlexAngle: Float = 0f,       // degrees, positive = forward lean
    val angulationAngle: Float = 0f,     // degrees, ankle tilt vs ski tilt
    val ankleSteeringAngle: Float = 0f,  // degrees, foot rotation vs leg
    val shellVibration: Float = 0f,      // 0-10 scaled
    val vibrationDamping: Float = 0f,    // 0-100%
    val absorptionScore: Float = 0f,     // 0-100
    val heading: Float = 0f,             // 0-360 degrees
    val distanceMm: Int = 0,
    val airborne: Boolean = false,
    val kneeStressWarning: Boolean = false,
    val lsm9ds1Present: Boolean = false,
    val vl6180xPresent: Boolean = false,
    val lateralStressDiff: Float = 0f,
) {
    companion object {
        fun parse(data: ByteArray): DualImuData? {
            if (data.size < 10) return null
            val flags = data[8].toInt() and 0xFF
            return DualImuData(
                bootFlexAngle = data[0].toFloat(),
                angulationAngle = data[1].toFloat(),
                ankleSteeringAngle = data[2].toFloat(),
                shellVibration = (data[3].toInt() and 0xFF) / 25f,
                vibrationDamping = (data[4].toInt() and 0xFF).toFloat(),
                absorptionScore = (data[5].toInt() and 0xFF).toFloat(),
                heading = ((data[6].toInt() and 0xFF) * 2).toFloat(),
                distanceMm = data[7].toInt() and 0xFF,
                airborne = (flags and 0x01) != 0,
                kneeStressWarning = (flags and 0x02) != 0,
                lsm9ds1Present = (flags and 0x04) != 0,
                vl6180xPresent = (flags and 0x08) != 0,
                lateralStressDiff = (data[9].toInt() and 0xFF).toFloat(),
            )
        }
    }
}

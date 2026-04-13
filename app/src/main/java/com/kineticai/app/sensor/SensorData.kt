package com.kineticai.app.sensor

data class ImuSample(
    val timestamp: Long,
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float,
    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float,
    val magX: Float,
    val magY: Float,
    val magZ: Float,
    val pressure: Float,
) {
    val accelMagnitude: Float
        get() = kotlin.math.sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ)

    val gyroMagnitude: Float
        get() = kotlin.math.sqrt(gyroX * gyroX + gyroY * gyroY + gyroZ * gyroZ)

    val gForce: Float
        get() = accelMagnitude / 9.81f
}

data class LocationSample(
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speed: Float,
    val bearing: Float,
    val accuracy: Float,
)

data class SkiFrame(
    val imu: ImuSample,
    val location: LocationSample?,
)

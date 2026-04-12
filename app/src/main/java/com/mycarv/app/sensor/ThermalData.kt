package com.mycarv.app.sensor

/**
 * Parsed thermal camera data from AMG8833 via boot sensor BLE.
 * 12 bytes: flags, person info, temperatures.
 */
data class ThermalVisionData(
    val personBehind: Boolean = false,
    val frostbiteWarning: Boolean = false,
    val indoors: Boolean = false,
    val fusedAlert: Boolean = false,
    val sensorPresent: Boolean = false,
    val personPixels: Int = 0,
    val personBearing: Float = 0f,       // degrees, -30 to +30
    val personElevation: Float = 0f,
    val fusedConfidence: Int = 0,        // 0-100
    val maxTemp: Float = 0f,             // °C
    val minTemp: Float = 0f,
    val snowTemp: Float = 0f,
    val ambientTemp: Float = 0f,
    val bootSurfaceTemp: Float = 0f,
    val personMaxTemp: Float = 0f,
) {
    companion object {
        fun parse(data: ByteArray): ThermalVisionData? {
            if (data.size < 12) return null
            val flags = data[0].toInt() and 0xFF
            return ThermalVisionData(
                personBehind = (flags and 0x01) != 0,
                frostbiteWarning = (flags and 0x02) != 0,
                indoors = (flags and 0x04) != 0,
                fusedAlert = (flags and 0x08) != 0,
                sensorPresent = (flags and 0x10) != 0,
                personPixels = data[1].toInt() and 0xFF,
                personBearing = data[2].toFloat(),
                fusedConfidence = data[3].toInt() and 0xFF,
                maxTemp = (data[4].toInt() and 0xFF) - 40f,
                minTemp = (data[5].toInt() and 0xFF) - 40f,
                snowTemp = (data[6].toInt() and 0xFF) - 40f,
                ambientTemp = (data[7].toInt() and 0xFF) - 40f,
                bootSurfaceTemp = (data[8].toInt() and 0xFF) - 40f,
                personMaxTemp = (data[9].toInt() and 0xFF) - 40f,
                personElevation = data[10].toFloat(),
            )
        }
    }
}

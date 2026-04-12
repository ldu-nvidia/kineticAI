package com.mycarv.app.sensor

/**
 * Parsed proximity radar data from boot sensor over BLE.
 * 4 bytes: level, distance, closingSpeed, energy
 */
data class ProximityData(
    val level: ProximityLevel = ProximityLevel.CLEAR,
    val distanceCm: Int = 0,
    val closingSpeedCmPerS: Int = 0,
    val energy: Int = 0,
) {
    val distanceM: Float get() = distanceCm / 100f
    val closingSpeedKmh: Float get() = closingSpeedCmPerS * 0.036f

    companion object {
        fun parse(data: ByteArray): ProximityData? {
            if (data.size < 4) return null
            return ProximityData(
                level = ProximityLevel.fromByte(data[0]),
                distanceCm = (data[1].toInt() and 0xFF) * 4,
                closingSpeedCmPerS = (data[2].toInt() and 0xFF) * 4,
                energy = data[3].toInt() and 0xFF,
            )
        }
    }
}

enum class ProximityLevel(val label: String) {
    CLEAR("Clear"),
    CAUTION("Caution"),
    WARNING("Warning"),
    DANGER("DANGER");

    companion object {
        fun fromByte(b: Byte): ProximityLevel = when (b.toInt()) {
            1 -> CAUTION; 2 -> WARNING; 3 -> DANGER; else -> CLEAR
        }
    }
}

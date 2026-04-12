package com.mycarv.app.sensor

/**
 * Parsed 3-segment kinematic chain data from boot sensor over BLE.
 * 12 bytes: knee flexion, valgus, ankle flex, angulation, ACL risk,
 * squat depth, rebound speed, chain order, separation, heading, cal, flags.
 */
data class TriSegmentData(
    val kneeFlexion: Float = 0f,          // degrees, 0-180
    val kneeValgus: Float = 0f,           // degrees, ±127 (positive = inward collapse)
    val ankleFlexion: Float = 0f,         // degrees, boot forward lean
    val ankleAngulation: Float = 0f,      // degrees, ankle tilt vs ski
    val aclRiskScore: Float = 0f,         // 0-100
    val squatDepth: Float = 0f,           // degrees, min knee flex in turn
    val reboundSpeed: Float = 0f,         // deg/s, knee extension rate
    val chainOrder: ChainOrder = ChainOrder.SIMULTANEOUS,
    val separationScore: Float = 0f,      // 0-100
    val shinHeading: Float = 0f,          // degrees, 0-360
    val bnoCalibration: Int = 0,          // 0-3
    val aclWarning: Boolean = false,
    val allSensorsPresent: Boolean = false,
    val bnoPresent: Boolean = false,
) {
    companion object {
        fun parse(data: ByteArray): TriSegmentData? {
            if (data.size < 12) return null
            val flags = data[11].toInt() and 0xFF
            return TriSegmentData(
                kneeFlexion = (data[0].toInt() and 0xFF).toFloat(),
                kneeValgus = data[1].toFloat(),
                ankleFlexion = data[2].toFloat(),
                ankleAngulation = data[3].toFloat(),
                aclRiskScore = (data[4].toInt() and 0xFF).toFloat(),
                squatDepth = (data[5].toInt() and 0xFF).toFloat(),
                reboundSpeed = (data[6].toInt() and 0xFF) * 4f,
                chainOrder = ChainOrder.fromByte(data[7]),
                separationScore = (data[8].toInt() and 0xFF).toFloat(),
                shinHeading = ((data[9].toInt() and 0xFF) * 2).toFloat(),
                bnoCalibration = data[10].toInt() and 0xFF,
                aclWarning = (flags and 0x01) != 0,
                allSensorsPresent = (flags and 0x02) != 0,
                bnoPresent = (flags and 0x04) != 0,
            )
        }
    }
}

enum class ChainOrder(val label: String) {
    BOTTOM_UP("Bottom-Up (Expert)"),
    TOP_DOWN("Top-Down"),
    SIMULTANEOUS("Simultaneous (Beginner)");

    companion object {
        fun fromByte(b: Byte): ChainOrder = when (b.toInt()) {
            0 -> BOTTOM_UP; 1 -> TOP_DOWN; else -> SIMULTANEOUS
        }
    }
}

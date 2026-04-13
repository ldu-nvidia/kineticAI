package com.kineticai.app.sensor

/**
 * Parsed microphone analysis data received from boot sensors over BLE.
 * 6 bytes: snowType, carveQuality, speedProxy, flags, ambientLevel, reserved
 */
data class MicData(
    val snowType: SnowType = SnowType.UNKNOWN,
    val carveQuality: CarveQuality = CarveQuality.UNKNOWN,
    val speedProxy: Int = 0,
    val fallDetected: Boolean = false,
    val bindingClick: Boolean = false,
    val ambientLevel: Int = 0,
) {
    companion object {
        fun parse(data: ByteArray): MicData? {
            if (data.size < 6) return null
            return MicData(
                snowType = SnowType.fromByte(data[0]),
                carveQuality = CarveQuality.fromByte(data[1]),
                speedProxy = data[2].toInt() and 0xFF,
                fallDetected = (data[3].toInt() and 0x01) != 0,
                bindingClick = (data[3].toInt() and 0x02) != 0,
                ambientLevel = data[4].toInt() and 0xFF,
            )
        }
    }
}

enum class SnowType(val label: String) {
    UNKNOWN("---"),
    POWDER("Powder"),
    GROOMED("Groomed"),
    ICE("Ice"),
    SLUSH("Slush"),
    CRUD("Crud");

    companion object {
        fun fromByte(b: Byte): SnowType = when (b.toInt()) {
            1 -> POWDER; 2 -> GROOMED; 3 -> ICE; 4 -> SLUSH; 5 -> CRUD
            else -> UNKNOWN
        }
    }
}

enum class CarveQuality(val label: String) {
    UNKNOWN("---"),
    CLEAN("Carving"),
    MODERATE("Some Skid"),
    SKIDDING("Skidding"),
    CHATTER("Chatter");

    companion object {
        fun fromByte(b: Byte): CarveQuality = when (b.toInt()) {
            1 -> CLEAN; 2 -> MODERATE; 3 -> SKIDDING; 4 -> CHATTER
            else -> UNKNOWN
        }
    }
}

package com.kineticai.app.snowboard

/**
 * Sport mode configuration for switching between analysis engines.
 * Controls which analysis engine, drills, feedback, and terminology to use.
 */
enum class SportMode {
    SKI,
    SNOWBOARD,
    CROSS_COUNTRY;

    val displayName: String get() = when (this) {
        SKI -> "Alpine Skiing"
        SNOWBOARD -> "Snowboarding"
        CROSS_COUNTRY -> "Cross-Country Skiing"
    }

    val shortName: String get() = when (this) {
        SKI -> "Ski"
        SNOWBOARD -> "Board"
        CROSS_COUNTRY -> "XC"
    }

    val turnLeftLabel: String get() = when (this) {
        SKI -> "Left"
        SNOWBOARD -> "Toeside"
        CROSS_COUNTRY -> "Left"
    }

    val turnRightLabel: String get() = when (this) {
        SKI -> "Right"
        SNOWBOARD -> "Heelside"
        CROSS_COUNTRY -> "Right"
    }

    val compositeScoreName: String get() = when (this) {
        SKI -> "Kinetic Score"
        SNOWBOARD -> "Ride Score"
        CROSS_COUNTRY -> "Stride Score"
    }

    val categories: List<String> get() = when (this) {
        SKI -> listOf("Balance", "Edging", "Rotary", "Pressure")
        SNOWBOARD -> listOf("Balance", "Edging", "Board Control", "Pressure", "Style")
        CROSS_COUNTRY -> listOf("Glide", "Power", "Symmetry", "Technique")
    }

    val sessionLabel: String get() = when (this) {
        SKI -> "Run"
        SNOWBOARD -> "Run"
        CROSS_COUNTRY -> "Session"
    }

    val startButtonLabel: String get() = when (this) {
        SKI -> "Start Run"
        SNOWBOARD -> "Start Run"
        CROSS_COUNTRY -> "Start Session"
    }

    val isCyclical: Boolean get() = this == CROSS_COUNTRY
}

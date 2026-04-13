package com.kineticai.app.snowboard

/**
 * Sport mode configuration for switching between Ski and Snowboard modes.
 * Controls which analysis engine, drills, feedback, and terminology to use.
 */
enum class SportMode {
    SKI,
    SNOWBOARD;

    val displayName: String get() = when (this) {
        SKI -> "Skiing"
        SNOWBOARD -> "Snowboarding"
    }

    val turnLeftLabel: String get() = when (this) {
        SKI -> "Left"
        SNOWBOARD -> "Toeside"
    }

    val turnRightLabel: String get() = when (this) {
        SKI -> "Right"
        SNOWBOARD -> "Heelside"
    }

    val compositeScoreName: String get() = when (this) {
        SKI -> "Kinetic Score"
        SNOWBOARD -> "Ride Score"
    }

    val categories: List<String> get() = when (this) {
        SKI -> listOf("Balance", "Edging", "Rotary", "Pressure")
        SNOWBOARD -> listOf("Balance", "Edging", "Board Control", "Pressure", "Style")
    }
}

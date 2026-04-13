package com.kineticai.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "runs")
data class RunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val durationMs: Long,
    val maxSpeedKmh: Float,
    val averageSpeedKmh: Float,
    val totalDistance: Float,
    val altitudeDrop: Float,
    val turnCount: Int,
    val leftTurns: Int,
    val rightTurns: Int,
    val maxGForce: Float,
    val startLatitude: Double,
    val startLongitude: Double,

    // 4 category scores (0–100)
    val balanceScore: Float,
    val edgingScore: Float,
    val rotaryScore: Float,
    val pressureScore: Float,

    // 12 individual metrics (0–100)
    val earlyForwardMovement: Float,
    val centeredBalance: Float,
    val edgeAngle: Float,
    val edgeAngleScore: Float,
    val earlyEdging: Float,
    val edgeSimilarity: Float,
    val progressiveEdgeBuild: Float,
    val parallelSkis: Float,
    val turnShape: Float,
    val outsideSkiPressure: Float,
    val pressureSmoothness: Float,
    val earlyWeightTransfer: Float,
    val transitionWeightRelease: Float,
    val avgGForce: Float,

    // Composite
    val skiIQ: Int,
)

package com.mycarv.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "turns",
    foreignKeys = [
        ForeignKey(
            entity = RunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("runId")],
)
data class TurnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: Long,
    val direction: String,
    val startTime: Long,
    val endTime: Long,
    val peakGyroZ: Float,
    val edgeAngle: Float,
    val earlyEdgingScore: Float,
    val progressiveEdgeScore: Float,
    val turnShapeScore: Float,
    val gForce: Float,
    val earlyForwardScore: Float,
    val centeredBalanceScore: Float,
    val weightReleaseScore: Float,
    val pressureSmoothnessScore: Float,
)

package com.mycarv.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {

    @Insert
    suspend fun insertRun(run: RunEntity): Long

    @Insert
    suspend fun insertSensorBatch(data: List<SensorDataEntity>)

    @Insert
    suspend fun insertTurns(turns: List<TurnEntity>)

    @Query("SELECT * FROM runs ORDER BY startTime DESC")
    fun getAllRuns(): Flow<List<RunEntity>>

    @Query("SELECT * FROM runs WHERE id = :runId")
    suspend fun getRunById(runId: Long): RunEntity?

    @Query("SELECT * FROM sensor_data WHERE runId = :runId ORDER BY timestamp")
    suspend fun getSensorDataForRun(runId: Long): List<SensorDataEntity>

    @Query("SELECT * FROM turns WHERE runId = :runId ORDER BY startTime")
    suspend fun getTurnsForRun(runId: Long): List<TurnEntity>

    @Query("DELETE FROM runs WHERE id = :runId")
    suspend fun deleteRun(runId: Long)

    @Query("SELECT COUNT(*) FROM runs")
    suspend fun getRunCount(): Int

    @Query("SELECT AVG(skiIQ) FROM runs")
    suspend fun getAverageSkiIQ(): Float?

    @Query("SELECT MAX(maxSpeedKmh) FROM runs")
    suspend fun getAllTimeMaxSpeed(): Float?

    @Query("SELECT SUM(totalDistance) FROM runs")
    suspend fun getTotalDistance(): Float?

    @Query("SELECT SUM(altitudeDrop) FROM runs")
    suspend fun getTotalVertical(): Float?

    @Query("SELECT MAX(skiIQ) FROM runs")
    suspend fun getPeakSkiIQ(): Int?

    @Query("SELECT * FROM runs ORDER BY startTime ASC")
    suspend fun getAllRunsChronological(): List<RunEntity>

    @Query("SELECT * FROM runs ORDER BY startTime DESC LIMIT 1")
    suspend fun getLatestRun(): RunEntity?
}

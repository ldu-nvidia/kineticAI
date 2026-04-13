package com.kineticai.app.data.repository

import com.kineticai.app.analysis.DetectedTurn
import com.kineticai.app.analysis.SkiMetrics
import com.kineticai.app.analysis.TurnDetector
import com.kineticai.app.data.db.AppDatabase
import com.kineticai.app.data.db.RunEntity
import com.kineticai.app.data.db.SensorDataEntity
import com.kineticai.app.data.db.TurnEntity
import com.kineticai.app.sensor.ImuSample
import com.kineticai.app.sensor.LocationSample
import kotlinx.coroutines.flow.Flow

class RunRepository(private val db: AppDatabase) {

    private val dao get() = db.runDao()

    fun allRuns(): Flow<List<RunEntity>> = dao.getAllRuns()

    suspend fun saveRun(
        metrics: SkiMetrics,
        turns: List<DetectedTurn>,
        sensorBuffer: List<Pair<ImuSample, LocationSample?>>,
    ): Long {
        val location = sensorBuffer.firstOrNull()?.second
        val runId = dao.insertRun(
            RunEntity(
                startTime = System.currentTimeMillis() - metrics.runDurationMs,
                endTime = System.currentTimeMillis(),
                durationMs = metrics.runDurationMs,
                maxSpeedKmh = metrics.maxSpeedKmh,
                averageSpeedKmh = metrics.averageSpeed * 3.6f,
                totalDistance = metrics.totalDistance,
                altitudeDrop = metrics.altitudeDrop,
                turnCount = metrics.turnCount,
                leftTurns = metrics.leftTurnCount,
                rightTurns = metrics.rightTurnCount,
                maxGForce = metrics.maxGForce,
                startLatitude = location?.latitude ?: 0.0,
                startLongitude = location?.longitude ?: 0.0,
                balanceScore = metrics.balanceScore,
                edgingScore = metrics.edgingScore,
                rotaryScore = metrics.rotaryScore,
                pressureScore = metrics.pressureScore,
                earlyForwardMovement = metrics.earlyForwardMovement,
                centeredBalance = metrics.centeredBalance,
                edgeAngle = metrics.edgeAngle,
                edgeAngleScore = metrics.edgeAngleScore,
                earlyEdging = metrics.earlyEdging,
                edgeSimilarity = metrics.edgeSimilarity,
                progressiveEdgeBuild = metrics.progressiveEdgeBuild,
                parallelSkis = metrics.parallelSkis,
                turnShape = metrics.turnShape,
                outsideSkiPressure = metrics.outsideSkiPressure,
                pressureSmoothness = metrics.pressureSmoothness,
                earlyWeightTransfer = metrics.earlyWeightTransfer,
                transitionWeightRelease = metrics.transitionWeightRelease,
                avgGForce = metrics.gForce,
                skiIQ = metrics.skiIQ,
            )
        )

        val sensorEntities = sensorBuffer.map { (imu, loc) ->
            SensorDataEntity(
                runId = runId,
                timestamp = imu.timestamp,
                accelX = imu.accelX,
                accelY = imu.accelY,
                accelZ = imu.accelZ,
                gyroX = imu.gyroX,
                gyroY = imu.gyroY,
                gyroZ = imu.gyroZ,
                latitude = loc?.latitude,
                longitude = loc?.longitude,
                altitude = loc?.altitude,
                speed = loc?.speed,
                edgeAngle = kotlin.math.abs(imu.accelX), // placeholder
                gForce = imu.gForce,
            )
        }
        sensorEntities.chunked(500).forEach { chunk ->
            dao.insertSensorBatch(chunk)
        }

        val turnEntities = turns.map {
            TurnEntity(
                runId = runId,
                direction = it.direction.name,
                startTime = it.startTime,
                endTime = it.endTime,
                peakGyroZ = it.peakGyroZ,
                edgeAngle = it.edgeAngle,
                earlyEdgingScore = it.earlyEdgingScore,
                progressiveEdgeScore = it.progressiveEdgeScore,
                turnShapeScore = it.turnShapeScore,
                gForce = it.gForce,
                earlyForwardScore = it.earlyForwardScore,
                centeredBalanceScore = it.centeredBalanceScore,
                weightReleaseScore = it.weightReleaseScore,
                pressureSmoothnessScore = it.pressureSmoothnessScore,
            )
        }
        if (turnEntities.isNotEmpty()) {
            dao.insertTurns(turnEntities)
        }

        return runId
    }

    suspend fun getRun(runId: Long) = dao.getRunById(runId)
    suspend fun getSensorData(runId: Long) = dao.getSensorDataForRun(runId)
    suspend fun getTurns(runId: Long) = dao.getTurnsForRun(runId)
    suspend fun deleteRun(runId: Long) = dao.deleteRun(runId)
    suspend fun getRunCount() = dao.getRunCount()
    suspend fun getAverageKineticScore() = dao.getAverageKineticScore()
    suspend fun getAllTimeMaxSpeed() = dao.getAllTimeMaxSpeed()
    suspend fun getTotalDistance() = dao.getTotalDistance()
    suspend fun getTotalVertical() = dao.getTotalVertical()
    suspend fun getPeakKineticScore() = dao.getPeakKineticScore()
    suspend fun getAllRunsChronological() = dao.getAllRunsChronological()
    suspend fun getLatestRun() = dao.getLatestRun()
}

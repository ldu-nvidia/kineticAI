package com.kineticai.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kineticai.app.KineticAIApp
import com.kineticai.app.analysis.DetectedTurn
import com.kineticai.app.analysis.FeedbackGenerator
import com.kineticai.app.analysis.SkiMetrics
import com.kineticai.app.analysis.TurnDirection
import com.kineticai.app.data.db.RunEntity
import com.kineticai.app.data.repository.RunRepository
import com.kineticai.app.sensor.LocationSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PostAnalysisViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = RunRepository((app as KineticAIApp).database)
    private val feedbackGen = FeedbackGenerator()

    private val _run = MutableStateFlow<RunEntity?>(null)
    val run: StateFlow<RunEntity?> = _run

    private val _feedback = MutableStateFlow<List<String>>(emptyList())
    val feedback: StateFlow<List<String>> = _feedback

    private val _speedProfile = MutableStateFlow<List<Float>>(emptyList())
    val speedProfile: StateFlow<List<Float>> = _speedProfile

    private val _edgeAngleProfile = MutableStateFlow<List<Float>>(emptyList())
    val edgeAngleProfile: StateFlow<List<Float>> = _edgeAngleProfile

    private val _gForceProfile = MutableStateFlow<List<Float>>(emptyList())
    val gForceProfile: StateFlow<List<Float>> = _gForceProfile

    private val _trajectory = MutableStateFlow<List<LocationSample>>(emptyList())
    val trajectory: StateFlow<List<LocationSample>> = _trajectory

    fun loadRun(runId: Long) {
        viewModelScope.launch {
            val runEntity = repo.getRun(runId) ?: return@launch
            _run.value = runEntity

            val sensorData = repo.getSensorData(runId)
            val turnData = repo.getTurns(runId)

            val step = (sensorData.size / 200).coerceAtLeast(1)
            _speedProfile.value = sensorData
                .filterIndexed { i, _ -> i % step == 0 }
                .mapNotNull { it.speed?.times(3.6f) }

            _edgeAngleProfile.value = sensorData
                .filterIndexed { i, _ -> i % step == 0 }
                .map { it.edgeAngle }

            _gForceProfile.value = sensorData
                .filterIndexed { i, _ -> i % step == 0 }
                .map { it.gForce }

            _trajectory.value = sensorData
                .filter { it.latitude != null && it.longitude != null }
                .distinctBy { "${String.format("%.6f", it.latitude)},${String.format("%.6f", it.longitude)}" }
                .map { LocationSample(
                    timestamp = it.timestamp,
                    latitude = it.latitude ?: 0.0,
                    longitude = it.longitude ?: 0.0,
                    altitude = it.altitude ?: 0.0,
                    speed = it.speed ?: 0f,
                    bearing = 0f,
                    accuracy = 0f,
                ) }

            val turns = turnData.map {
                DetectedTurn(
                    direction = TurnDirection.valueOf(it.direction),
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

            val metrics = SkiMetrics(
                maxSpeedKmh = runEntity.maxSpeedKmh,
                averageSpeed = runEntity.averageSpeedKmh / 3.6f,
                totalDistance = runEntity.totalDistance,
                altitudeDrop = runEntity.altitudeDrop,
                turnCount = runEntity.turnCount,
                leftTurnCount = runEntity.leftTurns,
                rightTurnCount = runEntity.rightTurns,
                maxGForce = runEntity.maxGForce,
                earlyForwardMovement = runEntity.earlyForwardMovement,
                centeredBalance = runEntity.centeredBalance,
                edgeAngle = runEntity.edgeAngle,
                edgeAngleScore = runEntity.edgeAngleScore,
                earlyEdging = runEntity.earlyEdging,
                edgeSimilarity = runEntity.edgeSimilarity,
                progressiveEdgeBuild = runEntity.progressiveEdgeBuild,
                parallelSkis = runEntity.parallelSkis,
                turnShape = runEntity.turnShape,
                outsideSkiPressure = runEntity.outsideSkiPressure,
                pressureSmoothness = runEntity.pressureSmoothness,
                earlyWeightTransfer = runEntity.earlyWeightTransfer,
                transitionWeightRelease = runEntity.transitionWeightRelease,
                gForce = runEntity.avgGForce,
                balanceScore = runEntity.balanceScore,
                edgingScore = runEntity.edgingScore,
                rotaryScore = runEntity.rotaryScore,
                pressureScore = runEntity.pressureScore,
                skiIQ = runEntity.skiIQ,
                runDurationMs = runEntity.durationMs,
            )

            _feedback.value = feedbackGen.generatePostRunFeedback(metrics, turns)
        }
    }
}

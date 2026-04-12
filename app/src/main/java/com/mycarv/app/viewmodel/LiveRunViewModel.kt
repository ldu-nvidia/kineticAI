package com.mycarv.app.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mycarv.app.analysis.FeedbackGenerator
import com.mycarv.app.analysis.SkiMetrics
import com.mycarv.app.sensor.ImuSample
import com.mycarv.app.sensor.LocationSample
import com.mycarv.app.sensor.DualImuData
import com.mycarv.app.sensor.TriSegmentData
import com.mycarv.app.sensor.MicData
import com.mycarv.app.sensor.ProximityData
import com.mycarv.app.sensor.ThermalVisionData
import com.mycarv.app.service.SkiTrackingService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LiveRunViewModel(app: Application) : AndroidViewModel(app) {

    private var service: SkiTrackingService? = null
    private var bound = false

    private val _metrics = MutableStateFlow(SkiMetrics())
    val metrics: StateFlow<SkiMetrics> = _metrics

    private val _latestTip = MutableStateFlow<FeedbackGenerator.CoachingTip?>(null)
    val latestTip: StateFlow<FeedbackGenerator.CoachingTip?> = _latestTip

    private val _tipVisible = MutableStateFlow(false)
    val tipVisible: StateFlow<Boolean> = _tipVisible

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private val _rawImu = MutableStateFlow<ImuSample?>(null)
    val rawImu: StateFlow<ImuSample?> = _rawImu

    private val _location = MutableStateFlow<LocationSample?>(null)
    val location: StateFlow<LocationSample?> = _location

    private val _trajectory = MutableStateFlow<List<LocationSample>>(emptyList())
    val trajectory: StateFlow<List<LocationSample>> = _trajectory

    private val _leftBootImu = MutableStateFlow<ImuSample?>(null)
    val leftBootImu: StateFlow<ImuSample?> = _leftBootImu

    private val _rightBootImu = MutableStateFlow<ImuSample?>(null)
    val rightBootImu: StateFlow<ImuSample?> = _rightBootImu

    private val _bleStatus = MutableStateFlow("Not connected")
    val bleStatus: StateFlow<String> = _bleStatus

    private val _leftMic = MutableStateFlow<MicData?>(null)
    val leftMic: StateFlow<MicData?> = _leftMic

    private val _rightMic = MutableStateFlow<MicData?>(null)
    val rightMic: StateFlow<MicData?> = _rightMic

    private val _proximity = MutableStateFlow<ProximityData?>(null)
    val proximity: StateFlow<ProximityData?> = _proximity

    private val _leftDualImu = MutableStateFlow<DualImuData?>(null)
    val leftDualImu: StateFlow<DualImuData?> = _leftDualImu
    private val _rightDualImu = MutableStateFlow<DualImuData?>(null)
    val rightDualImu: StateFlow<DualImuData?> = _rightDualImu

    private val _leftTriSegment = MutableStateFlow<TriSegmentData?>(null)
    val leftTriSegment: StateFlow<TriSegmentData?> = _leftTriSegment
    private val _rightTriSegment = MutableStateFlow<TriSegmentData?>(null)
    val rightTriSegment: StateFlow<TriSegmentData?> = _rightTriSegment

    private val _thermal = MutableStateFlow<ThermalVisionData?>(null)
    val thermal: StateFlow<ThermalVisionData?> = _thermal

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            try {
                val localBinder = binder as SkiTrackingService.LocalBinder
                service = localBinder.service
                bound = true
                observeService()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind service", e)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    init {
        try {
            val ctx = getApplication<Application>()
            ctx.startForegroundService(SkiTrackingService.startIntent(ctx))
            ctx.bindService(
                Intent(ctx, SkiTrackingService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start/bind tracking service", e)
        }
    }

    private fun observeService() {
        val svc = service ?: return

        viewModelScope.launch {
            svc.metrics.collect { _metrics.value = it }
        }

        viewModelScope.launch {
            svc.rawImu.collect { _rawImu.value = it }
        }

        viewModelScope.launch {
            svc.currentLocation.collect { _location.value = it }
        }

        viewModelScope.launch {
            svc.trajectoryPoints.collect { _trajectory.value = it }
        }

        viewModelScope.launch {
            svc.coachingTips.collect { tip ->
                _latestTip.value = tip
                _tipVisible.value = true
                delay(6000)
                _tipVisible.value = false
            }
        }

        viewModelScope.launch {
            svc.leftBootImu.collect { _leftBootImu.value = it }
        }

        viewModelScope.launch {
            svc.rightBootImu.collect { _rightBootImu.value = it }
        }

        viewModelScope.launch {
            svc.bleStatus.collect { _bleStatus.value = it }
        }

        viewModelScope.launch {
            svc.leftMic.collect { _leftMic.value = it }
        }

        viewModelScope.launch {
            svc.rightMic.collect { _rightMic.value = it }
        }

        viewModelScope.launch {
            svc.proximity.collect { _proximity.value = it }
        }

        viewModelScope.launch {
            svc.leftDualImu.collect { _leftDualImu.value = it }
        }
        viewModelScope.launch {
            svc.rightDualImu.collect { _rightDualImu.value = it }
        }
        viewModelScope.launch {
            svc.leftTriSegment.collect { _leftTriSegment.value = it }
        }
        viewModelScope.launch {
            svc.rightTriSegment.collect { _rightTriSegment.value = it }
        }
        viewModelScope.launch {
            svc.thermal.collect { _thermal.value = it }
        }
    }

    fun stopAndSave(onComplete: (Long) -> Unit) {
        val svc = service ?: return
        _isSaving.value = true

        viewModelScope.launch {
            try {
                val runId = svc.saveCurrentRun()
                svc.stopTracking()
                _isSaving.value = false
                onComplete(runId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save run", e)
                _isSaving.value = false
                svc.stopTracking()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (bound) {
            try {
                getApplication<Application>().unbindService(connection)
            } catch (_: Exception) {}
            bound = false
        }
    }

    companion object {
        private const val TAG = "LiveRunViewModel"
    }
}

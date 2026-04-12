package com.mycarv.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mycarv.app.MyCarvApp
import com.mycarv.app.R
import com.mycarv.app.analysis.DetectedTurn
import com.mycarv.app.analysis.FeedbackGenerator
import com.mycarv.app.analysis.RunLiftDetector
import com.mycarv.app.analysis.SkiAnalysisEngine
import com.mycarv.app.analysis.SkiMetrics
import com.mycarv.app.data.repository.RunRepository
import com.mycarv.app.sensor.BleCustomizer
import com.mycarv.app.sensor.BleImuManager
import com.mycarv.app.sensor.ImuSample
import com.mycarv.app.sensor.DualImuData
import com.mycarv.app.sensor.ThermalVisionData
import com.mycarv.app.sensor.TriSegmentData
import com.mycarv.app.sensor.MicData
import com.mycarv.app.sensor.ProximityData
import com.mycarv.app.sensor.LocationSample
import com.mycarv.app.sensor.LocationTracker
import com.mycarv.app.sensor.SensorCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SkiTrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var sensorCollector: SensorCollector
    private lateinit var locationTracker: LocationTracker
    private lateinit var bleImuManager: BleImuManager
    private var bleCustomizer: BleCustomizer? = null
    private val engine = SkiAnalysisEngine(sampleRateHz = 50f)
    private val runLiftDetector = RunLiftDetector()

    private val sensorBuffer = mutableListOf<Pair<ImuSample, LocationSample?>>()
    private var lastLocation: LocationSample? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val _metrics = MutableStateFlow(SkiMetrics())
    val metrics: StateFlow<SkiMetrics> = _metrics

    private val _rawImu = MutableStateFlow<ImuSample?>(null)
    val rawImu: StateFlow<ImuSample?> = _rawImu

    private val _currentLocation = MutableStateFlow<LocationSample?>(null)
    val currentLocation: StateFlow<LocationSample?> = _currentLocation

    private val _trajectoryPoints = MutableStateFlow<List<LocationSample>>(emptyList())
    val trajectoryPoints: StateFlow<List<LocationSample>> = _trajectoryPoints
    private val trajectoryBuffer = mutableListOf<LocationSample>()

    private val _coachingTips = MutableSharedFlow<FeedbackGenerator.CoachingTip>(extraBufferCapacity = 8)
    val coachingTips: SharedFlow<FeedbackGenerator.CoachingTip> = _coachingTips

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking

    private val _skiState = MutableStateFlow(RunLiftDetector.State.IDLE)
    val skiState: StateFlow<RunLiftDetector.State> = _skiState

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

    inner class LocalBinder : Binder() {
        val service: SkiTrackingService get() = this@SkiTrackingService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        sensorCollector = SensorCollector(this)
        locationTracker = LocationTracker(this)
        bleImuManager = BleImuManager(this)
    }

    fun getBleImuManager(): BleImuManager = bleImuManager

    private fun initBleCustomizer() {
        if (bleCustomizer == null) {
            bleCustomizer = BleCustomizer(bleImuManager)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                try {
                    startTracking()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start tracking", e)
                    stopSelf()
                }
            }
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        if (_isTracking.value) return

        engine.reset()
        runLiftDetector.reset()
        sensorBuffer.clear()
        trajectoryBuffer.clear()
        lastLocation = null
        _rawImu.value = null
        _currentLocation.value = null
        _trajectoryPoints.value = emptyList()
        _isTracking.value = true

        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground", e)
            _isTracking.value = false
            return
        }

        acquireWakeLock()

        scope.launch {
            try {
                sensorCollector.stream().collect { sample ->
                    _rawImu.value = sample

                    val turn = engine.processImu(sample)
                    sensorBuffer.add(sample to lastLocation)

                    _metrics.value = engine.metrics

                    if (sample.pressure > 0f) {
                        runLiftDetector.processBarometer(sample.timestamp, sample.pressure)
                        _skiState.value = runLiftDetector.state
                    }

                    turn?.let { handleTurn(it) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sensor collection error", e)
            }
        }

        scope.launch {
            try {
                locationTracker.stream().collect { loc ->
                    lastLocation = loc
                    _currentLocation.value = loc
                    trajectoryBuffer.add(loc)
                    _trajectoryPoints.value = trajectoryBuffer.toList()
                    engine.processLocation(loc)
                    runLiftDetector.processLocation(loc)
                    _skiState.value = runLiftDetector.state
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Location permission not granted", e)
            } catch (e: Exception) {
                Log.e(TAG, "Location tracking error", e)
            }
        }

        // Start BLE scanning for boot sensors
        try {
            bleImuManager.startScan()
        } catch (e: Exception) {
            Log.e(TAG, "BLE scan failed", e)
        }

        // Collect BLE IMU data from left boot
        scope.launch {
            bleImuManager.leftImu.collect { sample ->
                if (sample != null) {
                    _leftBootImu.value = sample
                    // Use left boot as primary IMU source when BLE is connected
                    if (bleImuManager.hasAnyConnection) {
                        val turn = engine.processImu(sample)
                        sensorBuffer.add(sample to lastLocation)
                        _metrics.value = engine.metrics
                        _rawImu.value = sample
                        turn?.let { handleTurn(it) }
                    }
                }
            }
        }

        // Collect BLE IMU data from right boot
        scope.launch {
            bleImuManager.rightImu.collect { sample ->
                if (sample != null) {
                    _rightBootImu.value = sample
                }
            }
        }

        // Collect mic data → feed into analysis engine
        scope.launch {
            bleImuManager.leftMic.collect { mic ->
                _leftMic.value = mic
                mic?.let {
                    engine.processCarveQuality(it.carveQuality.ordinal)
                    engine.processSnowType(it.snowType.label)
                }
            }
        }
        scope.launch {
            bleImuManager.rightMic.collect { _rightMic.value = it }
        }

        // Collect proximity data
        scope.launch {
            bleImuManager.proximity.collect { _proximity.value = it }
        }

        // Collect dual IMU data → feed boot flex + angulation into engine
        scope.launch {
            bleImuManager.leftDualImu.collect { dual ->
                _leftDualImu.value = dual
                dual?.let { engine.processBootFlex(it.bootFlexAngle, it.angulationAngle) }
                // Airborne detection from distance sensor
                dual?.let { engine.processAirborne(it.airborne, System.currentTimeMillis()) }
            }
        }
        scope.launch { bleImuManager.rightDualImu.collect { _rightDualImu.value = it } }

        // Collect tri-segment data → feed knee + kinetic chain into engine
        scope.launch {
            bleImuManager.leftTriSegment.collect { tri ->
                _leftTriSegment.value = tri
                tri?.let {
                    engine.processKneeData(
                        it.kneeFlexion, it.kneeValgus, it.aclRiskScore,
                        it.chainOrder.ordinal, it.separationScore,
                    )
                }
            }
        }
        scope.launch { bleImuManager.rightTriSegment.collect { _rightTriSegment.value = it } }

        // Collect thermal data
        scope.launch { bleImuManager.thermal.collect { _thermal.value = it } }

        // Monitor BLE connection status
        scope.launch {
            bleImuManager.scanStatus.collect { status ->
                _bleStatus.value = status
                if (bleImuManager.hasAnyConnection) {
                    initBleCustomizer()
                }
            }
        }

        // Auto-send power state to boots based on run/lift detection
        scope.launch {
            _skiState.collect { state ->
                bleCustomizer?.let { cust ->
                    when (state) {
                        RunLiftDetector.State.DESCENDING -> cust.setPowerState(BleCustomizer.PWR_SKIING)
                        RunLiftDetector.State.ASCENDING -> cust.setPowerState(BleCustomizer.PWR_LIFT)
                        RunLiftDetector.State.IDLE -> cust.setPowerState(BleCustomizer.PWR_SLEEP)
                        else -> {}
                    }
                }
            }
        }
    }

    private fun handleTurn(turn: DetectedTurn) {
        val tip = engine.feedbackGenerator.evaluate(engine.metrics, turn)
        tip?.let { _coachingTips.tryEmit(it) }
    }

    fun stopTracking() {
        if (!_isTracking.value) return
        _isTracking.value = false
        try { bleImuManager.disconnect() } catch (_: Exception) {}
        releaseWakeLock()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        stopSelf()
    }

    suspend fun saveCurrentRun(): Long {
        val repo = RunRepository((application as MyCarvApp).database)
        return repo.saveRun(engine.metrics, engine.allTurns, sensorBuffer.toList())
    }

    fun getCurrentMetrics(): SkiMetrics = engine.metrics
    fun getCurrentTurns(): List<DetectedTurn> = engine.allTurns

    fun getChairliftTip(): String? =
        engine.feedbackGenerator.generateChairliftTip(engine.metrics)

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, MyCarvApp.TRACKING_CHANNEL_ID)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(getString(R.string.tracking_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mycarv:tracking")
            wakeLock?.acquire(4 * 60 * 60 * 1000L)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {}
        wakeLock = null
    }

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SkiTrackingService"
        const val ACTION_START = "com.mycarv.ACTION_START_TRACKING"
        const val ACTION_STOP = "com.mycarv.ACTION_STOP_TRACKING"
        private const val NOTIFICATION_ID = 1

        fun startIntent(context: Context): Intent =
            Intent(context, SkiTrackingService::class.java).apply { action = ACTION_START }

        fun stopIntent(context: Context): Intent =
            Intent(context, SkiTrackingService::class.java).apply { action = ACTION_STOP }
    }
}

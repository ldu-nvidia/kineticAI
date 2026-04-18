package com.kineticai.app.sensor

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Manages BLE connections to two KineticAI boot IMU sensors (left + right).
 *
 * Scans for devices advertising the KineticAI service UUID, connects to both,
 * enables notifications on the IMU characteristic, and delivers parsed
 * ImuSample data via StateFlows.
 *
 * BLE Protocol (matches firmware):
 *   Service:  4d430001-0000-1000-8000-00805f9b34fb
 *   IMU data: 4d430002-... (24 bytes, notify at 50 Hz)
 *   Side:     4d430003-... (1 byte: 'L' or 'R', read)
 */
class BleImuManager(private val context: Context) {

    companion object {
        private const val TAG = "BleImuManager"
        val SERVICE_UUID: UUID = UUID.fromString("4d430001-0000-1000-8000-00805f9b34fb")
        val IMU_CHAR_UUID: UUID = UUID.fromString("4d430002-0000-1000-8000-00805f9b34fb")
        val SIDE_CHAR_UUID: UUID = UUID.fromString("4d430003-0000-1000-8000-00805f9b34fb")
        val MIC_CHAR_UUID: UUID = UUID.fromString("4d430005-0000-1000-8000-00805f9b34fb")
        val PROX_CHAR_UUID: UUID = UUID.fromString("4d430006-0000-1000-8000-00805f9b34fb")
        val DUAL_CHAR_UUID: UUID = UUID.fromString("4d430007-0000-1000-8000-00805f9b34fb")
        val TRI_CHAR_UUID: UUID = UUID.fromString("4d430008-0000-1000-8000-00805f9b34fb")
        val THERM_CHAR_UUID: UUID = UUID.fromString("4d430009-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    enum class ConnectionState { DISCONNECTED, SCANNING, CONNECTING, CONNECTED }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var scanner: BluetoothLeScanner? = null

    var leftGatt: BluetoothGatt? = null
        private set
    var rightGatt: BluetoothGatt? = null
        private set

    private val _leftState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val leftState: StateFlow<ConnectionState> = _leftState

    private val _rightState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val rightState: StateFlow<ConnectionState> = _rightState

    private val _leftImu = MutableStateFlow<ImuSample?>(null)
    val leftImu: StateFlow<ImuSample?> = _leftImu

    private val _rightImu = MutableStateFlow<ImuSample?>(null)
    val rightImu: StateFlow<ImuSample?> = _rightImu

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

    // Diagnostic: total IMU packets received since connect (for debug tracing)
    private var imuPacketCount = 0

    private val _scanStatus = MutableStateFlow("Idle")
    val scanStatus: StateFlow<String> = _scanStatus

    val isFullyConnected: Boolean
        get() = _leftState.value == ConnectionState.CONNECTED &&
                _rightState.value == ConnectionState.CONNECTED

    val hasAnyConnection: Boolean
        get() = _leftState.value == ConnectionState.CONNECTED ||
                _rightState.value == ConnectionState.CONNECTED

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _scanStatus.value = "Bluetooth not available"
            Log.e(TAG, "Bluetooth not available or not enabled")
            return
        }

        // Idempotent guard — if at least one boot is already connected, don't
        // tear down the existing GATT connection by rescanning. This is the
        // key fix for "connection drops when starting a run" — the tracking
        // service calls startScan() on every run start, and without this
        // guard we'd flip CONNECTED→SCANNING and reconnect from scratch.
        if (_leftState.value == ConnectionState.CONNECTED ||
            _rightState.value == ConnectionState.CONNECTED) {
            Log.i(TAG, "startScan() skipped — boot already connected")
            _scanStatus.value = "Connected"
            return
        }
        // Same for mid-connect — don't race a handshake in progress.
        if (_leftState.value == ConnectionState.CONNECTING ||
            _rightState.value == ConnectionState.CONNECTING) {
            Log.i(TAG, "startScan() skipped — connection in progress")
            return
        }

        scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            _scanStatus.value = "Scanner not available"
            return
        }

        // Stop any previous scan (SkiTrackingService may have started one at
        // boot). Without this, a duplicate startScan() fails with error 1
        // (SCAN_FAILED_ALREADY_STARTED) since the scanner is a singleton.
        try { scanner?.stopScan(scanCallback) } catch (_: Exception) {}

        _scanStatus.value = "Scanning..."
        _leftState.value = ConnectionState.SCANNING
        _rightState.value = ConnectionState.SCANNING

        // Filter by device name rather than service UUID. The firmware puts
        // the 128-bit service UUID in the scan response (not primary ad)
        // because name + UUID + flags exceed the 31-byte primary packet;
        // some Android BLE stacks (notably Samsung's) don't apply a
        // ServiceUuid filter against scan response data reliably.
        val filters = listOf(
            ScanFilter.Builder().setDeviceName("KineticAI-L").build(),
            ScanFilter.Builder().setDeviceName("KineticAI-R").build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner?.startScan(filters, settings, scanCallback)
            Log.i(TAG, "BLE scan started; looking for KineticAI-L / KineticAI-R")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start scan", e)
            _scanStatus.value = "Scan failed: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        try {
            scanner?.stopScan(scanCallback)
        } catch (_: Exception) {}
        _scanStatus.value = "Scan stopped"
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopScan()
        leftGatt?.close()
        rightGatt?.close()
        leftGatt = null
        rightGatt = null
        _leftState.value = ConnectionState.DISCONNECTED
        _rightState.value = ConnectionState.DISCONNECTED
        _leftImu.value = null
        _rightImu.value = null
        _scanStatus.value = "Disconnected"
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: return

            when (name) {
                "KineticAI-L" -> {
                    if (_leftState.value != ConnectionState.CONNECTED &&
                        _leftState.value != ConnectionState.CONNECTING
                    ) {
                        Log.i(TAG, "Found left boot: ${result.device.address}")
                        _leftState.value = ConnectionState.CONNECTING
                        _scanStatus.value = "Connecting to left boot..."
                        result.device.connectGatt(context, false, gattCallback)
                    }
                }
                "KineticAI-R" -> {
                    if (_rightState.value != ConnectionState.CONNECTED &&
                        _rightState.value != ConnectionState.CONNECTING
                    ) {
                        Log.i(TAG, "Found right boot: ${result.device.address}")
                        _rightState.value = ConnectionState.CONNECTING
                        _scanStatus.value = "Connecting to right boot..."
                        result.device.connectGatt(context, false, gattCallback)
                    }
                }
            }

            if (_leftState.value == ConnectionState.CONNECTED &&
                _rightState.value == ConnectionState.CONNECTED
            ) {
                stopScan()
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error: $errorCode")
            _scanStatus.value = "Scan failed (error $errorCode)"
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val name = gatt.device.name ?: "Unknown"

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to $name, requesting MTU...")
                // Request larger MTU BEFORE discovering services. Default MTU
                // is 23 (20-byte payload) which truncates our 34-byte motion
                // packet. 64 fits comfortably and is supported everywhere.
                // Service discovery is deferred to onMtuChanged().
                gatt.requestMtu(64)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from $name")
                when (name) {
                    "KineticAI-L" -> {
                        _leftState.value = ConnectionState.DISCONNECTED
                        leftGatt = null
                    }
                    "KineticAI-R" -> {
                        _rightState.value = ConnectionState.DISCONNECTED
                        rightGatt = null
                    }
                }
                updateScanStatus()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.i(TAG, "MTU negotiated with ${gatt.device.name}: mtu=$mtu status=$status")
            // Whether or not the negotiation succeeded, proceed with service
            // discovery. If MTU stayed at 23, we still get 20-byte packets and
            // will fail to parse; but at least we won't stall the pipeline.
            gatt.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed for ${gatt.device.name}")
                return
            }

            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                Log.e(TAG, "KineticAI service not found on ${gatt.device.name}")
                return
            }

            // Enable notifications on IMU characteristic. This MUST be the first
            // GATT op after service discovery — Android serializes GATT calls
            // (only one in flight at a time), so any prior readCharacteristic
            // would cause writeDescriptor() to be rejected with "prior command
            // is not finished" and the CCCD write silently fails, leaving
            // notifications disabled and no packets arriving.
            val imuChar = service.getCharacteristic(IMU_CHAR_UUID)
            if (imuChar != null) {
                val enabled = gatt.setCharacteristicNotification(imuChar, true)
                val descriptor = imuChar.getDescriptor(CCCD_UUID)
                val wrote = if (descriptor != null) {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                } else false
                Log.i(TAG, "Enabled IMU notifications on ${gatt.device.name}: localSet=$enabled, cccdWrite=$wrote")
            } else {
                Log.e(TAG, "IMU characteristic NOT FOUND on ${gatt.device.name}")
            }

            // NOTE: SIDE char read is skipped — we already know L/R from the
            // device name ("KineticAI-L" / "-R"). Reading it here would block
            // the CCCD write above.

            // Enable notifications on mic characteristic (after a delay for BLE queue)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val micChar = service.getCharacteristic(MIC_CHAR_UUID)
                if (micChar != null) {
                    @SuppressLint("MissingPermission")
                    gatt.setCharacteristicNotification(micChar, true)
                    val micDesc = micChar.getDescriptor(CCCD_UUID)
                    if (micDesc != null) {
                        @SuppressLint("MissingPermission")
                        micDesc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @SuppressLint("MissingPermission")
                        gatt.writeDescriptor(micDesc)
                    }
                }
            }, 500)

            // Enable notifications on proximity characteristic
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val proxChar = service.getCharacteristic(PROX_CHAR_UUID)
                if (proxChar != null) {
                    @SuppressLint("MissingPermission")
                    gatt.setCharacteristicNotification(proxChar, true)
                    val proxDesc = proxChar.getDescriptor(CCCD_UUID)
                    if (proxDesc != null) {
                        @SuppressLint("MissingPermission")
                        proxDesc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @SuppressLint("MissingPermission")
                        gatt.writeDescriptor(proxDesc)
                    }
                }
            }, 1000)

            val name = gatt.device.name ?: ""
            when (name) {
                "KineticAI-L" -> {
                    leftGatt = gatt
                    _leftState.value = ConnectionState.CONNECTED
                }
                "KineticAI-R" -> {
                    rightGatt = gatt
                    _rightState.value = ConnectionState.CONNECTED
                }
            }
            updateScanStatus()
        }

        @Deprecated("Deprecated in API 33, but needed for backward compat")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val data = characteristic.value ?: return

            // Throttled trace: every ~50th IMU packet (= ~1 s at 50 Hz per boot)
            if (characteristic.uuid == IMU_CHAR_UUID) {
                imuPacketCount++
                if (imuPacketCount % 50 == 0) {
                    Log.i(TAG, "IMU packets from ${gatt.device.name}: total=$imuPacketCount bytes=${data.size}")
                }
            }

            when (characteristic.uuid) {
                IMU_CHAR_UUID -> {
                    when (data.size) {
                        34 -> {
                            // Merged Motion Packet: IMU(12) + Dual(10) + Tri(12)
                            val sample = parseImuPacketCompressed(data.copyOfRange(0, 12))
                            val dual = DualImuData.parse(data.copyOfRange(12, 22))
                            val tri = TriSegmentData.parse(data.copyOfRange(22, 34))
                            val name = gatt.device.name
                            if (name == "KineticAI-L") {
                                _leftImu.value = sample
                                if (dual != null) _leftDualImu.value = dual
                                if (tri != null) _leftTriSegment.value = tri
                            } else if (name == "KineticAI-R") {
                                _rightImu.value = sample
                                if (dual != null) _rightDualImu.value = dual
                                if (tri != null) _rightTriSegment.value = tri
                            }
                        }
                        12 -> {
                            val sample = parseImuPacketCompressed(data)
                            when (gatt.device.name) {
                                "KineticAI-L" -> _leftImu.value = sample
                                "KineticAI-R" -> _rightImu.value = sample
                            }
                        }
                        24 -> {
                            val sample = parseImuPacket(data)
                            when (gatt.device.name) {
                                "KineticAI-L" -> _leftImu.value = sample
                                "KineticAI-R" -> _rightImu.value = sample
                            }
                        }
                    }
                }
                MIC_CHAR_UUID -> {
                    if (data.size >= 18) {
                        // Merged Environment Packet: Mic(6) + Thermal(12)
                        val mic = MicData.parse(data.copyOfRange(0, 6))
                        val therm = ThermalVisionData.parse(data.copyOfRange(6, 18))
                        val name = gatt.device.name
                        if (mic != null) {
                            if (name == "KineticAI-L") _leftMic.value = mic
                            else _rightMic.value = mic
                        }
                        if (therm != null) _thermal.value = therm
                    } else if (data.size >= 6) {
                        val mic = MicData.parse(data)
                        if (mic != null) {
                            when (gatt.device.name) {
                                "KineticAI-L" -> _leftMic.value = mic
                                "KineticAI-R" -> _rightMic.value = mic
                            }
                        }
                    }
                }
                PROX_CHAR_UUID -> {
                    val prox = ProximityData.parse(data)
                    if (prox != null) {
                        _proximity.value = prox
                    }
                }
                DUAL_CHAR_UUID -> {
                    val dual = DualImuData.parse(data)
                    if (dual != null) {
                        when (gatt.device.name) {
                            "KineticAI-L" -> _leftDualImu.value = dual
                            "KineticAI-R" -> _rightDualImu.value = dual
                        }
                    }
                }
                TRI_CHAR_UUID -> {
                    val tri = TriSegmentData.parse(data)
                    if (tri != null) {
                        when (gatt.device.name) {
                            "KineticAI-L" -> _leftTriSegment.value = tri
                            "KineticAI-R" -> _rightTriSegment.value = tri
                        }
                    }
                }
                THERM_CHAR_UUID -> {
                    val therm = ThermalVisionData.parse(data)
                    if (therm != null) _thermal.value = therm
                }
            }
        }
    }

    private fun parseImuPacket(data: ByteArray): ImuSample {
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        return ImuSample(
            timestamp = System.currentTimeMillis(),
            accelX = buf.getFloat(0),
            accelY = buf.getFloat(4),
            accelZ = buf.getFloat(8),
            gyroX = buf.getFloat(12),
            gyroY = buf.getFloat(16),
            gyroZ = buf.getFloat(20),
            magX = 0f, magY = 0f, magZ = 0f, pressure = 0f,
        )
    }

    /**
     * Parse compressed 12-byte int16 IMU packet.
     * Decode: accel = int16 / 208.97 (m/s²), gyro = int16 / 938.88 (rad/s)
     */
    private fun parseImuPacketCompressed(data: ByteArray): ImuSample {
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        return ImuSample(
            timestamp = System.currentTimeMillis(),
            accelX = buf.getShort(0) / 208.97f,
            accelY = buf.getShort(2) / 208.97f,
            accelZ = buf.getShort(4) / 208.97f,
            gyroX = buf.getShort(6) / 938.88f,
            gyroY = buf.getShort(8) / 938.88f,
            gyroZ = buf.getShort(10) / 938.88f,
            magX = 0f, magY = 0f, magZ = 0f, pressure = 0f,
        )
    }

    private fun updateScanStatus() {
        val l = if (_leftState.value == ConnectionState.CONNECTED) "L:OK" else "L:--"
        val r = if (_rightState.value == ConnectionState.CONNECTED) "R:OK" else "R:--"
        _scanStatus.value = "$l  $r"
    }
}

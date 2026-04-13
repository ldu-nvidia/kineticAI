package com.kineticai.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class SensorCollector(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val barometer = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    fun stream(samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_GAME): Flow<ImuSample> =
        callbackFlow {
            var lastAccel = floatArrayOf(0f, 0f, 0f)
            var lastGyro = floatArrayOf(0f, 0f, 0f)
            var lastMag = floatArrayOf(0f, 0f, 0f)
            var lastPressure = 0f

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    when (event.sensor.type) {
                        Sensor.TYPE_ACCELEROMETER -> {
                            lastAccel = event.values.copyOf()
                            val sample = ImuSample(
                                timestamp = System.currentTimeMillis(),
                                accelX = lastAccel[0],
                                accelY = lastAccel[1],
                                accelZ = lastAccel[2],
                                gyroX = lastGyro[0],
                                gyroY = lastGyro[1],
                                gyroZ = lastGyro[2],
                                magX = lastMag[0],
                                magY = lastMag[1],
                                magZ = lastMag[2],
                                pressure = lastPressure,
                            )
                            trySend(sample)
                        }
                        Sensor.TYPE_GYROSCOPE -> lastGyro = event.values.copyOf()
                        Sensor.TYPE_MAGNETIC_FIELD -> lastMag = event.values.copyOf()
                        Sensor.TYPE_PRESSURE -> lastPressure = event.values[0]
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            accelerometer?.let {
                sensorManager.registerListener(listener, it, samplingPeriodUs)
            }
            gyroscope?.let {
                sensorManager.registerListener(listener, it, samplingPeriodUs)
            }
            magnetometer?.let {
                sensorManager.registerListener(listener, it, samplingPeriodUs)
            }
            barometer?.let {
                sensorManager.registerListener(listener, it, samplingPeriodUs)
            }

            awaitClose {
                sensorManager.unregisterListener(listener)
            }
        }
}

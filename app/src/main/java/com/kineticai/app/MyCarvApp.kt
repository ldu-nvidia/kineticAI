package com.kineticai.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.kineticai.app.data.db.AppDatabase
import com.kineticai.app.sensor.BleImuManager

class KineticAIApp : Application() {

    lateinit var database: AppDatabase
        private set

    // App-wide BLE manager singleton. Survives composable recompositions and
    // configuration changes. Lifecycle is tied to the Application process.
    val bleImuManager: BleImuManager by lazy { BleImuManager(this) }

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.create(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            TRACKING_CHANNEL_ID,
            getString(R.string.tracking_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val TRACKING_CHANNEL_ID = "ski_tracking"
    }
}

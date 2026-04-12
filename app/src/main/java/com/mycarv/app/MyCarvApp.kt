package com.mycarv.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.mycarv.app.data.db.AppDatabase

class MyCarvApp : Application() {

    lateinit var database: AppDatabase
        private set

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

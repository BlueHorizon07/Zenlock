package com.zenlock

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.zenlock.service.ScreenFilterService

class ZenApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(
                ScreenFilterService.CHANNEL_ID,
                getString(R.string.filter_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.filter_channel_desc)
                setShowBadge(false)
            },
        )
    }
}

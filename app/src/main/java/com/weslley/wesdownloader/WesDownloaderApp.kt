package com.weslley.wesdownloader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.weslley.wesdownloader.download.DownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WesDownloaderApp : Application() {
    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannel()
        applicationScope.launch {
            container.repository.markActiveInterrupted()
            runCatching { container.extractor.initialize() }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            DownloadService.CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.notification_channel_description) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

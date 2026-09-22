package com.weslley.wesdownloader.download

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.weslley.wesdownloader.MainActivity
import com.weslley.wesdownloader.R
import com.weslley.wesdownloader.WesDownloaderApp
import com.weslley.wesdownloader.domain.AppError
import com.weslley.wesdownloader.domain.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val container by lazy { (application as WesDownloaderApp).container }
    private val jobs = ConcurrentHashMap<String, Job>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(EXTRA_ID) ?: return START_NOT_STICKY
        if (intent.action == ACTION_CANCEL) {
            cancelDownload(id)
            return START_NOT_STICKY
        }

        val job = scope.launch(start = CoroutineStart.LAZY) { process(id, startId) }
        if (jobs.putIfAbsent(id, job) != null) return START_NOT_STICKY
        if (jobs.size == 1) {
            startForeground(notificationId(id), buildNotification("Preparando download", 0, id, true))
        } else {
            notify(id, "Preparando download", 0, true)
        }
        job.start()
        return START_NOT_STICKY
    }

    private suspend fun process(id: String, startId: Int) {
        try {
            val item = container.repository.get(id) ?: return
            if (!container.storage.hasSpaceFor(item.estimatedBytes)) throw AppError.NoSpace()
            container.repository.updateProgress(id, DownloadStatus.DOWNLOADING, 1, "Preparando download")
            val directory = container.storage.tempDirectory(id)
            val output = container.extractor.download(item, directory) { progress ->
                val status = if (progress.stage.startsWith("Processando")) DownloadStatus.PROCESSING else DownloadStatus.DOWNLOADING
                container.repository.updateProgress(id, status, progress.percent, progress.stage)
                notify(id, progress.stage, progress.percent, true)
            }
            container.repository.updateProgress(id, DownloadStatus.PROCESSING, 96, "Salvando em Downloads")
            notify(id, "Salvando em Downloads", 96, true)
            val (uri, fileName) = container.storage.publish(output, item.title, item.mode)
            container.repository.finish(id, uri.toString(), fileName)
            container.storage.deleteTemporary(id)
        } catch (_: CancellationException) {
            // O estado de cancelamento e registrado por cancelDownload.
        } catch (error: Exception) {
            Log.e(TAG, "Falha durante o download $id", error)
            val current = container.repository.get(id)
            if (current?.status != DownloadStatus.CANCELLED) {
                val message = when (error) {
                    is AppError -> error.message ?: "Falha no download"
                    else -> DownloadFailureMessage.from(error)
                }
                container.repository.fail(id, DownloadStatus.FAILED, message)
                notify(id, message, current?.progress ?: 0, false)
            }
        } finally {
            jobs.remove(id)
            getSystemService(android.app.NotificationManager::class.java).cancel(notificationId(id))
            if (jobs.isEmpty()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
            }
        }
    }

    private fun cancelDownload(id: String) {
        container.extractor.cancel(id)
        jobs[id]?.cancel()
        scope.launch {
            container.repository.fail(id, DownloadStatus.CANCELLED, "Download cancelado")
            container.storage.deleteTemporary(id)
            getSystemService(android.app.NotificationManager::class.java).cancel(notificationId(id))
            if (jobs.isEmpty()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun notify(id: String, stage: String, progress: Int, ongoing: Boolean) {
        getSystemService(android.app.NotificationManager::class.java)
            .notify(notificationId(id), buildNotification(stage, progress, id, ongoing))
    }

    private fun buildNotification(stage: String, progress: Int, id: String, ongoing: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancelIntent = PendingIntent.getService(
            this,
            notificationId(id),
            Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("WesDownloader")
            .setContentText(stage)
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setProgress(100, progress, false)
            .apply {
                if (ongoing) addAction(0, getString(R.string.notification_cancel), cancelIntent)
            }
            .build()
    }

    override fun onDestroy() {
        jobs.keys.forEach { container.extractor.cancel(it) }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DownloadService"
        const val CHANNEL_ID = "wesdownloader_downloads"
        private const val ACTION_START = "com.weslley.wesdownloader.START"
        private const val ACTION_CANCEL = "com.weslley.wesdownloader.CANCEL"
        private const val EXTRA_ID = "download_id"

        fun start(context: Context, id: String) {
            val intent = Intent(context, DownloadService::class.java).setAction(ACTION_START).putExtra(EXTRA_ID, id)
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context, id: String) {
            val intent = Intent(context, DownloadService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_ID, id)
            context.startService(intent)
        }

        private fun notificationId(id: String): Int = id.hashCode() and 0x7fffffff
    }
}

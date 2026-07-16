package org.akkirrai.hibiki.core.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.withAppPreferencesLanguage
import kotlin.math.roundToInt

@UnstableApi
class HibikiDownloadService : DownloadService(
    DOWNLOAD_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    OfflineMediaCache.DOWNLOAD_NOTIFICATION_CHANNEL_ID,
    R.string.download_notification_channel_name,
    R.string.download_notification_channel_description,
) {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withAppPreferencesLanguage())
    }

    override fun getDownloadManager(): DownloadManager {
        val manager = OfflineMediaCache.getDownloadManager(this)
        OfflineDownloadQueue.install(this, manager)
        return manager
    }

    override fun getScheduler(): Scheduler? = null

    override fun onDestroy() {
        // DownloadService stops itself when DownloadManager becomes idle, but its
        // last foreground notification may otherwise remain visible as active.
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification {
        val activeDownload = downloads.firstOrNull { it.state == Download.STATE_DOWNLOADING }
        val activeQueuedCount = downloads.count {
            it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED
        }

        val titleText: String
        val contentText: String
        val isOngoing: Boolean

        if (activeDownload != null) {
            val meta = parseDownloadNotificationMeta(activeDownload.request.data)
            val (storedCompleted, storedTotal) = OfflineDownloadQueue.getNotificationProgress(this)
            val sessionTotal = storedTotal
                .coerceAtLeast(storedCompleted + activeQueuedCount)
                .coerceAtLeast(1)
            val sessionCompleted = storedCompleted.coerceIn(0, sessionTotal)
            val episodePercent = activeDownload.percentDownloaded
                .takeIf { it.isFinite() && it >= 0f }
                ?.roundToInt()
                ?.coerceIn(0, 100)
                ?: 0

            titleText = meta?.displayTitle ?: getString(R.string.download_notification_channel_name)
            contentText = getString(
                R.string.download_notification_progress,
                sessionCompleted,
                sessionTotal,
                episodePercent,
            )
            isOngoing = true
        } else if (activeQueuedCount > 0) {
            titleText = getString(R.string.download_notification_channel_name)
            contentText = getString(R.string.download_notification_active, activeQueuedCount)
            isOngoing = true
        } else {
            titleText = getString(R.string.download_notification_channel_name)
            contentText = getString(R.string.download_notification_waiting)
            isOngoing = false
        }

        val episodePercent = activeDownload?.percentDownloaded
            ?.takeIf { it.isFinite() && it >= 0f }
            ?.roundToInt()
            ?.coerceIn(0, 100)

        return NotificationCompat.Builder(this, OfflineMediaCache.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setOngoing(isOngoing)
            .setOnlyAlertOnce(true)
            .setProgress(
                100,
                episodePercent ?: 0,
                (activeDownload != null && episodePercent == null) ||
                    (activeDownload == null && activeQueuedCount > 0),
            )
            .build()
    }

    companion object {
        private const val DOWNLOAD_NOTIFICATION_ID = 31_000

        fun showPreparingNotification(context: Context) {
            val localizedContext = context.applicationContext.withAppPreferencesLanguage()
            val manager = localizedContext.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        OfflineMediaCache.DOWNLOAD_NOTIFICATION_CHANNEL_ID,
                        localizedContext.getString(R.string.download_notification_channel_name),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = localizedContext.getString(R.string.download_notification_channel_description)
                    }
                )
            }
            val (completed, total) = OfflineDownloadQueue.getNotificationProgress(localizedContext)
            manager.notify(
                DOWNLOAD_NOTIFICATION_ID,
                NotificationCompat.Builder(localizedContext, OfflineMediaCache.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(localizedContext.getString(R.string.download_notification_channel_name))
                    .setContentText(
                        localizedContext.getString(
                            R.string.download_notification_preparing,
                            completed,
                            total.coerceAtLeast(1),
                        )
                    )
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setProgress(0, 0, true)
                    .build()
            )
        }

        fun cancelPreparingNotification(context: Context) {
            context.applicationContext
                .getSystemService(NotificationManager::class.java)
                .cancel(DOWNLOAD_NOTIFICATION_ID)
        }
    }
}

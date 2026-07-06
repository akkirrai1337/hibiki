package org.akkirrai.hibiki.core.download

import android.app.Notification
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import org.akkirrai.hibiki.R

@UnstableApi
class HibikiDownloadService : DownloadService(
    DOWNLOAD_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    OfflineMediaCache.DOWNLOAD_NOTIFICATION_CHANNEL_ID,
    R.string.download_notification_channel_name,
    R.string.download_notification_channel_description,
) {
    override fun getDownloadManager(): DownloadManager {
        val manager = OfflineMediaCache.getDownloadManager(this)
        OfflineDownloadQueue.install(this, manager)
        return manager
    }

    override fun getScheduler(): Scheduler? = null

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
            val (sessionCompleted, sessionTotal) = OfflineDownloadQueue.getNotificationProgress(this)

            titleText = meta?.displayTitle ?: getString(R.string.download_notification_channel_name)
            contentText = getString(
                R.string.download_notification_progress,
                sessionCompleted,
                sessionTotal,
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

        return NotificationCompat.Builder(this, OfflineMediaCache.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setOngoing(isOngoing)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, activeQueuedCount > 0 || activeDownload != null)
            .build()
    }

    private companion object {
        const val DOWNLOAD_NOTIFICATION_ID = 31_000
    }
}

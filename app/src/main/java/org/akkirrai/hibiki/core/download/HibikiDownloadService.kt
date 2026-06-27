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
        val activeCount = downloads.count { it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED }
        return NotificationCompat.Builder(this, OfflineMediaCache.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.download_notification_channel_name))
            .setContentText(
                if (activeCount > 0) {
                    getString(R.string.download_notification_active, activeCount)
                } else {
                    getString(R.string.download_notification_waiting)
                }
            )
            .setOngoing(activeCount > 0)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, activeCount > 0)
            .build()
    }

    private companion object {
        const val DOWNLOAD_NOTIFICATION_ID = 31_000
    }
}

package org.akkirrai.hibiki.core.anilist

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import org.akkirrai.hibiki.MainActivity
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.log.AppLogger

/**
 * The automatic sync, run when the app starts and never in the background.
 *
 * It runs only when it is worth it: the last sync (manual or automatic) was at least [INTERVAL_MILLIS]
 * ago and the library holds a title that was not there at that sync. A manual sync resets both, which
 * is what starts the wait over.
 *
 * What it does is the safe half: the import runs on its own, and what Hibiki would send to AniList is
 * only worked out and announced in a notification - writing to the account stays behind the preview
 * the user confirms.
 */
object AniListAutoSync {
    private const val TAG = "AniListAutoSync"
    private const val INTERVAL_MILLIS = 12L * 60 * 60 * 1000
    private const val CHANNEL_ID = "anilist_sync"
    private const val NOTIFICATION_ID = 7301

    suspend fun runIfDue(context: Context) {
        val appContext = context.applicationContext
        val sync = AniListLibrarySync(appContext)
        if (!sync.autoEnabled || sync.sourceId.isBlank()) return
        val lastSync = sync.lastSyncAt
        // Nothing to be automatic about until a first sync has been done by hand.
        if (lastSync == 0L || System.currentTimeMillis() - lastSync < INTERVAL_MILLIS) return
        val signedIn = AniListRepository(appContext).let { repository ->
            val token = repository.currentAccessToken()
            repository.close()
            token != null
        }
        if (sync.useAccount && !signedIn) return
        if (!sync.useAccount && sync.userName.isBlank()) return
        if (!sync.hasNewTitles()) return

        try {
            sync.run()
            if (sync.useAccount && signedIn) {
                val plan = AniListLibraryPush(appContext).plan()
                val changes = plan.items.size + plan.removals.size
                if (changes > 0) notify(appContext, changes, plan.removals.size)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            // Left for the next launch: the sync time only moves when a run finishes.
            AppLogger.w(TAG, "Automatic AniList sync did not finish", error)
        }
    }

    private fun notify(context: Context, changes: Int, removals: Int) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.anilist_auto_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_hibiki)
                .setContentTitle(context.getString(R.string.anilist_auto_notification_title))
                .setContentText(
                    if (removals > 0) {
                        context.getString(R.string.anilist_auto_notification_removals, changes, removals)
                    } else {
                        context.getString(R.string.anilist_auto_notification_text, changes)
                    },
                )
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }
}

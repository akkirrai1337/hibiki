package org.akkirrai.hibiki.core.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.akkirrai.hibiki.MainActivity
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.withAppPreferencesLanguage

/** Fires when a scheduled episode reminder's alarm goes off; shows the "episode is out" notification. */
class EpisodeReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val animeId = EpisodeReminderScheduler.readAnimeId(intent) ?: return
        val animeTitle = EpisodeReminderScheduler.readAnimeTitle(intent).orEmpty()
        val episodeNumber = EpisodeReminderScheduler.readEpisodeNumber(intent)
        EpisodeReminderScheduler.clearScheduledState(context, animeId)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val localizedContext = context.applicationContext.withAppPreferencesLanguage()
        val manager = localizedContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    localizedContext.getString(R.string.episode_reminder_notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = localizedContext.getString(R.string.episode_reminder_notification_channel_description)
                },
            )
        }

        val contentIntent = PendingIntent.getActivity(
            localizedContext,
            animeId.hashCode(),
            Intent(localizedContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(localizedContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(
                localizedContext.getString(R.string.episode_reminder_notification_title, animeTitle),
            )
            .setContentText(
                localizedContext.getString(R.string.episode_reminder_notification_text, episodeNumber),
            )
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        manager.notify(animeId.hashCode(), notification)
    }

    companion object {
        private const val CHANNEL_ID = "episode_reminders"
    }
}

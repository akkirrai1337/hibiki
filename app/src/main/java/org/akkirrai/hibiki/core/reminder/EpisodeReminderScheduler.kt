package org.akkirrai.hibiki.core.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Schedules a one-shot local notification for the next episode of an ongoing anime, keyed by
 * anime id. Backed by AlarmManager (inexact wake) instead of WorkManager -- the app has no
 * WorkManager dependency and this is a single fire-and-forget alarm, not recurring work.
 */
object EpisodeReminderScheduler {
    private const val PREFS_NAME = "episode_reminders"
    private const val EXTRA_ANIME_ID = "anime_id"
    private const val EXTRA_ANIME_TITLE = "anime_title"
    private const val EXTRA_EPISODE_NUMBER = "episode_number"

    fun isScheduled(context: Context, animeId: String, episodeNumber: Int): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(animeId, -1) == episodeNumber
    }

    fun schedule(context: Context, animeId: String, animeTitle: String, episodeNumber: Int, atEpochSeconds: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = pendingIntentFor(context, animeId, animeTitle, episodeNumber)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpochSeconds * 1000, pendingIntent)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(animeId, episodeNumber)
            .apply()
    }

    fun cancel(context: Context, animeId: String, episodeNumber: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager?.cancel(pendingIntentFor(context, animeId, animeTitle = "", episodeNumber))
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(animeId)
            .apply()
    }

    private fun pendingIntentFor(context: Context, animeId: String, animeTitle: String, episodeNumber: Int): PendingIntent {
        val intent = Intent(context, EpisodeReminderReceiver::class.java).apply {
            data = Uri.parse("hibiki://episode-reminder/$animeId")
            putExtra(EXTRA_ANIME_ID, animeId)
            putExtra(EXTRA_ANIME_TITLE, animeTitle)
            putExtra(EXTRA_EPISODE_NUMBER, episodeNumber)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    internal fun readAnimeId(intent: Intent): String? = intent.getStringExtra(EXTRA_ANIME_ID)
    internal fun readAnimeTitle(intent: Intent): String? = intent.getStringExtra(EXTRA_ANIME_TITLE)
    internal fun readEpisodeNumber(intent: Intent): Int = intent.getIntExtra(EXTRA_EPISODE_NUMBER, 0)

    internal fun clearScheduledState(context: Context, animeId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(animeId)
            .apply()
    }
}

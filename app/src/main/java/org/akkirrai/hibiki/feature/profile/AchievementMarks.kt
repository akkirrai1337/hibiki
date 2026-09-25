package org.akkirrai.hibiki.feature.profile

import android.content.Context

/**
 * The highest library-based figures the profile has ever reached: titles, completed titles and distinct genres.
 *
 * Achievements such as "First title" or "Collector" are read off the library, so removing titles (or their
 * data) would take an earned achievement away again. An achievement is earned for good, so the best figure
 * seen is kept and used whenever the library is smaller than it once was.
 */
internal class AchievementMarks(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val titles: Int get() = prefs.getInt(KEY_TITLES, 0)
    val completed: Int get() = prefs.getInt(KEY_COMPLETED, 0)
    val genres: Int get() = prefs.getInt(KEY_GENRES, 0)

    fun record(titles: Int, completed: Int, genres: Int) {
        if (titles <= this.titles && completed <= this.completed && genres <= this.genres) return
        prefs.edit()
            .putInt(KEY_TITLES, maxOf(titles, this.titles))
            .putInt(KEY_COMPLETED, maxOf(completed, this.completed))
            .putInt(KEY_GENRES, maxOf(genres, this.genres))
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "profile_achievement_marks"
        const val KEY_TITLES = "titles"
        const val KEY_COMPLETED = "completed"
        const val KEY_GENRES = "genres"
    }
}

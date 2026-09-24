package org.akkirrai.hibiki.core.model

import android.content.Context
import androidx.annotation.StringRes
import org.akkirrai.beakokit.model.AnimeReleaseStatus
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.LanguageMode
import org.akkirrai.hibiki.app.settings.withAppPreferencesLanguage
import org.akkirrai.hibiki.app.settings.withLanguage

/**
 * Release status text in both directions.
 *
 * Sources deliver a free-text status ("finished airing", "вышел", ...), which
 * [AnimeReleaseStatus.from] recognises. The app then stores the status as display text in the
 * language it was shown in, so reading it back has to recognise that text too. Both directions use
 * the string resources: the words the app displays are, by definition, the ones it must recognise,
 * in every language the app is translated to, so adding a translation adds its vocabulary.
 */
object ReleaseStatusText {
    @Volatile
    private var displayTerms: Map<String, AnimeReleaseStatus> = emptyMap()

    /** Reads the status words of every app language from resources; call once at startup. */
    fun initialize(context: Context) {
        val appContext = context.applicationContext
        val terms = mutableMapOf<String, AnimeReleaseStatus>()
        val languages = LanguageMode.entries.filter { it.tag != null }
        for (status in AnimeReleaseStatus.entries) {
            val resource = status.labelResource()
            terms[appContext.getString(resource).normalized()] = status
            languages.forEach { language ->
                terms[appContext.withLanguage(language).getString(resource).normalized()] = status
            }
        }
        displayTerms = terms
    }

    /** The status for [text], which may be a source's raw status or one the app displayed earlier. */
    fun parse(text: String?): AnimeReleaseStatus {
        val raw = AnimeReleaseStatus.from(text)
        if (raw != AnimeReleaseStatus.UNKNOWN) return raw
        return displayTerms[text.orEmpty().normalized()] ?: AnimeReleaseStatus.UNKNOWN
    }

    /** The status name in the app language. */
    fun label(context: Context, status: AnimeReleaseStatus): String =
        context.withAppPreferencesLanguage().getString(status.labelResource())

    @StringRes
    private fun AnimeReleaseStatus.labelResource(): Int = when (this) {
        AnimeReleaseStatus.ONGOING -> R.string.library_filter_status_ongoing
        AnimeReleaseStatus.RELEASED -> R.string.library_filter_status_released
        AnimeReleaseStatus.ANNOUNCEMENT -> R.string.library_filter_status_announced
        AnimeReleaseStatus.UNKNOWN -> R.string.details_release_date_unknown
    }

    private fun String.normalized(): String = trim().lowercase()
}

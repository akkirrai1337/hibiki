package eu.kanade.tachiyomi.animesource

import androidx.preference.PreferenceScreen

/** API v14 marker for extensions that expose source settings. */
interface ConfigurableAnimeSource {
    fun setupPreferenceScreen(screen: PreferenceScreen)
}

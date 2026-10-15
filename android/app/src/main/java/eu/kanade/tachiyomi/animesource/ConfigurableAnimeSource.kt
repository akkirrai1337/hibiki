package eu.kanade.tachiyomi.animesource

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Extensions that expose source settings (v14); [getSourcePreferences] since v17. */
interface ConfigurableAnimeSource : AnimeSource {
    /** The same "source_<id>" store the settings screen writes (see ApkPreferences). */
    fun getSourcePreferences(): SharedPreferences =
        Injekt.get<Application>().getSharedPreferences(preferenceKey(), Context.MODE_PRIVATE)

    fun setupPreferenceScreen(screen: PreferenceScreen)
}

fun ConfigurableAnimeSource.preferenceKey(): String = "source_$id"

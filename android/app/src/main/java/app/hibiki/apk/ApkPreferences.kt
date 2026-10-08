package app.hibiki.apk

import android.content.Context
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.TwoStatePreference
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import org.json.JSONArray
import org.json.JSONObject

/**
 * An APK source's own settings as the app's settings rows (SourceSetting in shared/types.ts). The
 * extension declares them by filling a PreferenceScreen (ConfigurableAnimeSource) and reads them
 * back from its source_<id> SharedPreferences, as in Aniyomi and the Kotlin Hibiki app:
 *
 *   switch / checkbox -> TOGGLE ("true" / "false")
 *   list              -> SELECT (the entry values, labelled by the entries)
 *   text              -> TEXT
 *
 * Other kinds (multi-select lists, plain links) are left out. A change goes through the
 * preference's own change listener first, as the extension expects, and is kept only if it agrees.
 * Main thread only: it is the extension's UI code.
 */
internal object ApkPreferences {
    private fun screen(context: Context, adapter: ApkSourceAdapter): PreferenceScreen? {
        val configurable = adapter.catalogue as? ConfigurableAnimeSource ?: return null
        return runCatching {
            val manager = PreferenceManager(context)
            manager.sharedPreferencesName = "source_${adapter.aniyomiSourceId}"
            manager.createPreferenceScreen(context).also(configurable::setupPreferenceScreen)
        }.getOrNull()
    }

    /** Every supported row, with the title of the category it sits in (if any). */
    private fun rows(group: PreferenceGroup, category: String? = null): List<Pair<Preference, String?>> =
        (0 until group.preferenceCount).map(group::getPreference).filter { it.isVisible }.flatMap { preference ->
            when (preference) {
                is PreferenceGroup -> rows(preference, preference.title?.toString()?.takeIf(String::isNotBlank) ?: category)
                is TwoStatePreference, is ListPreference, is EditTextPreference -> if (preference.key.isNullOrBlank()) emptyList() else listOf(preference to category)
                else -> emptyList()
            }
        }

    fun describe(context: Context, adapter: ApkSourceAdapter): JSONArray {
        val out = JSONArray()
        val screen = screen(context, adapter) ?: return out
        for ((preference, category) in rows(screen)) {
            val title = preference.title?.toString().orEmpty().ifBlank { preference.key }
            val row = JSONObject()
                .put("key", preference.key)
                .put("title", if (category != null) "$category · $title" else title)
            // A list's summary is usually just its current choice ("%s"), which the chips already show.
            val current = (preference as? ListPreference)?.let { it.entry?.toString() ?: it.value }
            val summary = preference.summary?.toString()?.trim()?.takeIf { it.isNotBlank() && it != current && it != (preference as? ListPreference)?.value }
            if (summary != null) row.put("description", summary)
            when (preference) {
                is TwoStatePreference -> row.put("type", "TOGGLE").put("default", preference.isChecked)
                is ListPreference -> {
                    val entries = preference.entries.orEmpty()
                    val values = preference.entryValues.orEmpty()
                    row.put("type", "SELECT").put("options", JSONArray(values.indices.map { i ->
                        JSONObject().put("id", values[i].toString()).put("title", entries.getOrNull(i)?.toString() ?: values[i].toString())
                    }))
                    preference.value?.let { row.put("default", it) }
                }
                is EditTextPreference -> row.put("type", "TEXT").put("default", preference.text.orEmpty())
            }
            out.put(row)
        }
        return out
    }

    /** The current values, as the settings screen reads them (strings). */
    fun values(context: Context, adapter: ApkSourceAdapter): JSONObject {
        val out = JSONObject()
        val screen = screen(context, adapter) ?: return out
        for ((preference, _) in rows(screen)) {
            when (preference) {
                is TwoStatePreference -> out.put(preference.key, preference.isChecked.toString())
                is ListPreference -> preference.value?.let { out.put(preference.key, it) }
                is EditTextPreference -> out.put(preference.key, preference.text.orEmpty())
            }
        }
        return out
    }

    fun write(context: Context, adapter: ApkSourceAdapter, key: String, value: String?) {
        val screen = screen(context, adapter) ?: throw IllegalStateException("${adapter.name} has no settings")
        val preference = rows(screen).firstOrNull { it.first.key == key }?.first
            ?: throw IllegalArgumentException("${adapter.name} has no setting named $key")
        when (preference) {
            is TwoStatePreference -> {
                val next = value == "true"
                if (preference.callChangeListener(next)) preference.isChecked = next
            }
            is ListPreference -> if (value != null && preference.callChangeListener(value)) preference.value = value
            is EditTextPreference -> {
                val next = value.orEmpty()
                if (preference.callChangeListener(next)) preference.text = next
            }
        }
    }
}

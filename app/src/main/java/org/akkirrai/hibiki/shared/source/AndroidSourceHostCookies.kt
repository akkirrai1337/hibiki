package org.akkirrai.hibiki.shared.source

import android.content.Context
import android.content.SharedPreferences
import org.akkirrai.beakokit.api.SourceHostCookies
import org.akkirrai.beakokit.api.SourceHostRequirements
import org.akkirrai.beakokit.api.SourceId
import org.json.JSONObject

/** Android persistent cookie jar isolated by the external source ID. */
internal class AndroidSourceHostCookies(
    context: Context,
    sourceId: SourceId,
    override val requirements: SourceHostRequirements,
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        "beakokit_source_cookies_${sourceId.value}",
        Context.MODE_PRIVATE,
    ),
) : SourceHostCookies() {
    override suspend fun cookiesForUrl(url: String): Map<String, String> =
        readCookieStore().optJSONObject(url)?.let(::readCookieObject).orEmpty()

    override suspend fun storeResponseCookies(url: String, cookies: Map<String, String>) {
        val store = readCookieStore()
        val existing = store.optJSONObject(url) ?: JSONObject()
        cookies.forEach { (name, value) -> existing.put(name, value) }
        store.put(url, existing)
        persist(store)
    }

    override suspend fun clearCookies(url: String) {
        val store = readCookieStore()
        store.remove(url)
        persist(store)
    }

    private fun readCookieStore(): JSONObject {
        val raw = preferences.getString(COOKIES_KEY, null) ?: return JSONObject()
        return runCatching { JSONObject(raw) }.getOrElse { error ->
            throw IllegalStateException("Source cookie storage is corrupted", error)
        }
    }

    private fun readCookieObject(objectValue: JSONObject): Map<String, String> = buildMap {
        objectValue.keys().forEach { key ->
            put(key, objectValue.getString(key))
        }
    }

    private fun persist(store: JSONObject) {
        check(
            preferences.edit()
                .putString(COOKIES_KEY, store.toString())
                .commit(),
        ) { "Could not persist source cookies" }
    }

    private companion object {
        const val COOKIES_KEY = "cookies"
    }
}

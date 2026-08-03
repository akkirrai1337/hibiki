package org.akkirrai.hibiki.shared.source

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults
import org.akkirrai.beakokit.api.SourceHostCookies
import org.akkirrai.beakokit.api.SourceHostRequirements
import org.akkirrai.beakokit.api.SourceId

/** iOS persistent cookie jar isolated by the external source ID. */
@OptIn(ExperimentalForeignApi::class)
internal class IosSourceHostCookies(
    sourceId: SourceId,
    override val requirements: SourceHostRequirements,
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : SourceHostCookies() {
    private val storageKey = "beakokit.source.${sourceId.value}.cookies"
    private val json = Json
    private val serializer = MapSerializer(String.serializer(), MapSerializer(String.serializer(), String.serializer()))

    override suspend fun cookiesForUrl(url: String): Map<String, String> =
        readStore()[url].orEmpty()

    override suspend fun storeResponseCookies(url: String, cookies: Map<String, String>) {
        val store = readStore().toMutableMap()
        val existing = store[url].orEmpty().toMutableMap()
        existing.putAll(cookies)
        store[url] = existing
        persist(store)
    }

    override suspend fun clearCookies(url: String) {
        val store = readStore().toMutableMap()
        store.remove(url)
        persist(store)
    }

    private fun readStore(): Map<String, Map<String, String>> {
        val raw = defaults.stringForKey(storageKey) ?: return emptyMap()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrElse { error ->
            throw IllegalStateException("Source cookie storage is corrupted", error)
        }
    }

    private fun persist(store: Map<String, Map<String, String>>) {
        defaults.setObject(json.encodeToString(serializer, store), forKey = storageKey)
    }
}

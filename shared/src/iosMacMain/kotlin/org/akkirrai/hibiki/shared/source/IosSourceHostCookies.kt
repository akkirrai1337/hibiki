package org.akkirrai.hibiki.shared.source

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSData
import org.akkirrai.beakokit.api.SourceHostCookies
import org.akkirrai.beakokit.api.SourceHostRequirements
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.hibiki.shared.security.hibiki_keychain_read
import org.akkirrai.hibiki.shared.security.hibiki_keychain_write

/** iOS cookie jar isolated by source ID and stored as one encrypted Keychain record. */
@OptIn(ExperimentalForeignApi::class)
internal class IosSourceHostCookies(
    sourceId: SourceId,
    override val requirements: SourceHostRequirements,
) : SourceHostCookies() {
    private val account = "beakokit.source.${sourceId.value}.cookies"
    private val json = Json
    private val serializer = MapSerializer(String.serializer(), MapSerializer(String.serializer(), String.serializer()))

    override suspend fun cookiesForUrl(url: String): Map<String, String> = readStore()[url].orEmpty()

    override suspend fun storeResponseCookies(url: String, cookies: Map<String, String>) {
        val store = readStore().toMutableMap()
        store[url] = (store[url].orEmpty() + cookies)
        persist(store)
    }

    override suspend fun clearCookies(url: String) {
        val store = readStore().toMutableMap()
        store.remove(url)
        persist(store)
    }

    private fun readStore(): Map<String, Map<String, String>> =
        hibiki_keychain_read(KEYCHAIN_SERVICE, account)
            ?.toByteArray()
            ?.decodeToString()
            ?.let { raw ->
                runCatching { json.decodeFromString(serializer, raw) }.getOrElse { error ->
                    throw IllegalStateException("Source cookie storage is corrupted", error)
                }
            }
            .orEmpty()

    private fun persist(store: Map<String, Map<String, String>>) {
        val bytes = json.encodeToString(serializer, store).encodeToByteArray()
        val saved = bytes.usePinned { pinned ->
            hibiki_keychain_write(
                KEYCHAIN_SERVICE,
                account,
                if (bytes.isEmpty()) null else pinned.addressOf(0).reinterpret<UByteVar>(),
                bytes.size.toULong(),
            )
        }
        check(saved) { "Could not persist source cookies in Keychain" }
    }

    private companion object {
        const val KEYCHAIN_SERVICE = "org.akkirrai.hibiki.external-source-cookies"
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).also { bytes ->
    if (bytes.isNotEmpty()) {
        bytes.usePinned { pinned ->
            platform.posix.memcpy(pinned.addressOf(0), this.bytes, length)
        }
    }
}

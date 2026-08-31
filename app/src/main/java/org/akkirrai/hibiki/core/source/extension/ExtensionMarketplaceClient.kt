package org.akkirrai.hibiki.core.source.extension

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.akkirrai.beakokit.extension.ScriptExtensionManifest
import org.akkirrai.beakokit.extension.PlayerResolverExtensionManifest

@Serializable
data class MarketplaceExtension(
    val id: String,
    val name: String,
    val version: String,
    val author: String? = null,
    val website: String? = null,
    val iconUrl: String? = null,
    val lang: String,
    val capabilities: List<String> = emptyList(),
    val resolverDependencies: List<String> = emptyList(),
    val isNsfw: Boolean = false,
    val type: String = "source",
    val manifestUrl: String,
)

@Serializable
data class MarketplaceIndex(
    val schemaVersion: Int,
    val extensions: List<MarketplaceExtension> = emptyList(),
)

class ExtensionMarketplaceException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Fetches the hibiki-sources marketplace index and individual extension manifests over plain
 * HTTP(S). Both are served by raw.githubusercontent.com as `text/plain`, so ktor's
 * ContentNegotiation won't auto-deserialize them (it only fires on a matching Content-Type) -
 * every response is read with `bodyAsText()` and decoded manually instead.
 *
 * Each hibiki-sources extension is published as two files, `<id>.manifest.json` (metadata only)
 * and `<id>.js` (the actual payload) - kept apart so the JS is real, readable, indented source in
 * the repo instead of an escaped one-line JSON string. [fetchManifest] fetches both and merges
 * them into the single manifest+payload JSON [org.akkirrai.beakokit.extension.ScriptExtensionRepository.install]
 * expects on-device; that merge is the only place a full single-file manifest ever exists again.
 */
class ExtensionMarketplaceClient(
    private val client: HttpClient,
    private val indexUrl: String = DEFAULT_INDEX_URL,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchIndex(): MarketplaceIndex {
        val response = client.get(stableRepositoryUrl(indexUrl)) { noCacheHeaders() }
        if (!response.status.isSuccess()) {
            throw ExtensionMarketplaceException("Repository index request failed: HTTP ${response.status.value}")
        }
        return runCatching { json.decodeFromString(MarketplaceIndex.serializer(), response.bodyAsText()) }
            .getOrElse { error -> throw ExtensionMarketplaceException("Repository index is invalid", error) }
    }

    /** Fetches `<id>.manifest.json` + `<id>.js` and merges them into one manifest+payload JSON. */
    suspend fun fetchManifest(extension: MarketplaceExtension): String {
        val metadataJson = fetchText(extension.manifestUrl, "manifest for '${extension.id}'")
        val metadata = runCatching { json.decodeFromString(ScriptExtensionManifest.serializer(), metadataJson) }
            .getOrElse { error -> throw ExtensionMarketplaceException("Manifest for '${extension.id}' is invalid", error) }

        val payload = metadata.payload.takeIf(String::isNotBlank)
            ?: fetchText(payloadUrlFor(extension.manifestUrl), "payload for '${extension.id}'")

        return json.encodeToString(ScriptExtensionManifest.serializer(), metadata.copy(payload = payload))
    }

    suspend fun fetchPlayerResolverManifest(extension: MarketplaceExtension): String {
        val metadataJson = fetchText(extension.manifestUrl, "resolver manifest for '${extension.id}'")
        val metadata = runCatching {
            json.decodeFromString(PlayerResolverExtensionManifest.serializer(), metadataJson)
        }.getOrElse { error -> throw ExtensionMarketplaceException("Resolver manifest for '${extension.id}' is invalid", error) }
        val payload = metadata.payload.takeIf(String::isNotBlank)
            ?: fetchText(payloadUrlFor(extension.manifestUrl), "resolver payload for '${extension.id}'")
        return json.encodeToString(PlayerResolverExtensionManifest.serializer(), metadata.copy(payload = payload))
    }

    private suspend fun fetchText(url: String, label: String): String {
        val response = client.get(stableRepositoryUrl(url)) { noCacheHeaders() }
        if (!response.status.isSuccess()) {
            throw ExtensionMarketplaceException("Request for $label failed: HTTP ${response.status.value}")
        }
        return response.bodyAsText()
    }

    // raw.githubusercontent.com sits behind Fastly, which caches each URL for a few minutes
    // (Cache-Control: max-age=300) independently of the others and, being a public/anonymous
    // CDN, doesn't let a client's own no-cache request headers force a bypass - that would let
    // any client trivially bust the cache for everyone. A per-request query parameter is the
    // only reliable bypass: it makes each fetch a distinct URL as far as the edge cache is
    // concerned, so it always falls through to origin. Without this, a freshly-bumped
    // manifest/payload/index.json can keep serving the pre-update copy to the marketplace for a
    // while after it's published, making "Update" intermittently look unavailable or reinstall
    // the same old version.
    private fun HttpRequestBuilder.noCacheHeaders() {
        headers {
            append(HttpHeaders.CacheControl, "no-cache, no-store")
            append(HttpHeaders.Pragma, "no-cache")
        }
        url.parameters.append("cachebust", System.currentTimeMillis().toString())
    }

    /** `.../extensions/<id>.manifest.json` -> `.../extensions/<id>.js`, hibiki-sources' file-pairing convention. */
    private fun payloadUrlFor(manifestUrl: String): String {
        if (manifestUrl.endsWith(MANIFEST_SUFFIX)) {
            return manifestUrl.removeSuffix(MANIFEST_SUFFIX) + ".js"
        }
        throw ExtensionMarketplaceException("Manifest URL doesn't follow the <id>$MANIFEST_SUFFIX convention: $manifestUrl")
    }

    /**
     * GitHub Raw can keep the short branch form (`.../main/...`) stale after a branch update.
     * The fully-qualified branch ref follows the same branch but reliably reaches the fresh
     * object. The stored repository URL remains untouched; this is only request normalization.
     */
    internal fun stableRepositoryUrl(url: String): String = GITHUB_RAW_MAIN_URL.replace(url) { match ->
        "${match.groupValues[1]}/refs/heads/main/${match.groupValues[2]}"
    }

    companion object {
        const val DEFAULT_INDEX_URL =
            "https://raw.githubusercontent.com/akkirrai1337/hibiki-sources/main/repository/index.json"
        private const val MANIFEST_SUFFIX = ".manifest.json"
        private val GITHUB_RAW_MAIN_URL = Regex(
            "^(https://raw\\.githubusercontent\\.com/[^/]+/[^/]+)/main/(.+)$",
        )
    }
}

/** Basic-semver (`x.y.z`) comparison; treats any non-matching string as equal (no update prompt). */
fun isExtensionVersionNewer(remoteVersion: String, installedVersion: String): Boolean {
    val remote = remoteVersion.trim().split('.').mapNotNull(String::toIntOrNull)
    val installed = installedVersion.trim().split('.').mapNotNull(String::toIntOrNull)
    if (remote.isEmpty() || installed.isEmpty()) return false
    for (index in 0 until maxOf(remote.size, installed.size)) {
        val comparison = remote.getOrElse(index) { 0 }.compareTo(installed.getOrElse(index) { 0 })
        if (comparison != 0) return comparison > 0
    }
    return false
}

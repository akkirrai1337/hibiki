package org.akkirrai.hibiki.core.source.extension

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import java.net.URI

/** What the extension list shows for one extension, whether it comes from a repository or is installed. */
data class MarketplaceExtension(
    val id: String,
    val name: String,
    val version: String,
    val iconUrl: String? = null,
    val lang: String,
    val isNsfw: Boolean = false,
)

/** One entry in the APK index format used by Aniyomi/Mihon-compatible repositories. */
@Serializable
data class ApkRepositoryExtension(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String = "",
    val version: String = "",
    val nsfw: Int = 0,
    @Transient val downloadUrl: String = "",
    @Transient val iconUrl: String = "",
)

class ExtensionMarketplaceException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Fetches an Aniyomi/Mihon-style extension repository (an `index.min.json` array next to `apk/`
 * and `icon/` folders) and downloads its APKs over HTTPS. raw.githubusercontent.com serves these as
 * `text/plain`, so ktor's ContentNegotiation would not deserialize them; every response is read
 * with `bodyAsText()` and decoded manually instead.
 */
class ExtensionMarketplaceClient(
    private val client: HttpClient,
    private val indexUrl: String = DEFAULT_INDEX_URL,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Loads the repository index and resolves each extension's APK and icon URLs. */
    suspend fun fetchCatalog(): List<ApkRepositoryExtension> {
        val response = getHttps(indexUrl, "Repository index")
        if (!response.status.isSuccess()) {
            throw ExtensionMarketplaceException("Repository index request failed: HTTP ${response.status.value}")
        }
        return try {
            val root = json.parseToJsonElement(response.bodyAsText())
            check(root is JsonArray) { "not an APK extension index" }
            json.decodeFromJsonElement(ListSerializer(ApkRepositoryExtension.serializer()), root)
                .map { extension ->
                    extension.copy(
                        downloadUrl = resolveApkUrl(extension.apk),
                        iconUrl = resolveRepositoryAssetUrl("icon/${extension.pkg}.png"),
                    )
                }
        } catch (error: Exception) {
            throw ExtensionMarketplaceException("Repository index is invalid or uses an unsupported format", error)
        }
    }

    suspend fun downloadApk(extension: ApkRepositoryExtension, target: java.io.File) {
        val url = extension.downloadUrl.ifBlank { resolveApkUrl(extension.apk) }
        var response = getHttps(url, "APK for '${extension.name}'")
        if (response.status.value == 404 && extension.apk.isFileNameOnly()) {
            response.bodyAsText()
            val siblingUrl = resolveIndexSiblingApkUrl(extension.apk)
            if (siblingUrl != url) response = getHttps(siblingUrl, "APK for '${extension.name}'")
        }
        if (!response.status.isSuccess()) {
            throw ExtensionMarketplaceException("APK download failed: HTTP ${response.status.value}")
        }
        val bytes = response.body<ByteArray>()
        withContext(Dispatchers.IO) { target.writeBytes(bytes) }
        if (target.length() == 0L) {
            target.delete()
            throw ExtensionMarketplaceException("Downloaded APK is empty")
        }
    }

    private fun resolveApkUrl(apkPath: String): String = runCatching {
        val path = URI(apkPath)
        if (path.isAbsolute) return path.toString()
        val relativePath = if (apkPath.isFileNameOnly()) "apk/$apkPath" else apkPath
        URI(indexUrl).resolve(relativePath).toString()
    }.getOrElse { throw ExtensionMarketplaceException("APK path is invalid: $apkPath", it) }

    private fun resolveIndexSiblingApkUrl(apkPath: String): String = runCatching {
        URI(indexUrl).resolve(apkPath).toString()
    }.getOrElse { throw ExtensionMarketplaceException("APK path is invalid: $apkPath", it) }

    private fun resolveRepositoryAssetUrl(path: String): String = runCatching {
        URI(indexUrl).resolve(path).toString()
    }.getOrElse { throw ExtensionMarketplaceException("Repository asset path is invalid: $path", it) }

    private fun String.isFileNameOnly(): Boolean =
        isNotBlank() && !contains('/') && !contains('\\') && !startsWith("https://", ignoreCase = true)

    private suspend fun getHttps(url: String, label: String) = client.get(stableRepositoryUrl(url)) {
        requireHttpsUrl(url, label)
        noCacheHeaders()
    }.also { response ->
        if (!response.call.request.url.protocol.name.equals("https", ignoreCase = true)) {
            throw ExtensionMarketplaceException("$label was redirected to a non-HTTPS URL")
        }
    }

    private fun requireHttpsUrl(url: String, label: String) {
        val uri = runCatching { URI(url) }.getOrNull()
        if (uri?.scheme?.equals("https", ignoreCase = true) != true || uri.host.isNullOrBlank()) {
            throw ExtensionMarketplaceException("$label URL must use HTTPS")
        }
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

    /**
     * GitHub Raw can keep the short branch form (`.../main/...`) stale after a branch update.
     * The fully-qualified branch ref follows the same branch but reliably reaches the fresh
     * object. The stored repository URL remains untouched; this is only request normalization.
     */
    internal fun stableRepositoryUrl(url: String): String = GITHUB_RAW_MAIN_URL.replace(url) { match ->
        "${match.groupValues[1]}/refs/heads/main/${match.groupValues[2]}"
    }

    companion object {
        /** The repository offered by default: Yuzono's Aniyomi anime extensions. */
        const val DEFAULT_INDEX_URL = "https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json"

        /** The repository Hibiki shipped with before it moved to Aniyomi extensions; replaced on upgrade. */
        const val LEGACY_INDEX_URL =
            "https://raw.githubusercontent.com/akkirrai1337/hibiki-sources/main/repository/index.json"
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

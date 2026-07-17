package org.akkirrai.hibiki.core.update

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.akkirrai.hibiki.core.network.AndroidHttpClientFactory

private const val RELEASES_URL = "https://api.github.com/repos/akkirrai1337/hibiki/releases?per_page=20"
private const val APK_CONTENT_TYPE = "application/vnd.android.package-archive"

data class AppUpdate(
    val version: String,
    val releaseUrl: String,
    val apkUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long,
    val notes: String,
    val isDownloaded: Boolean = false,
)

class AppUpdateRepository(
    private val currentVersionName: String,
    private val client: HttpClient = AndroidHttpClientFactory.create(),
) {
    suspend fun findAvailableUpdate(): AppUpdate? {
        val response: HttpResponse = client.get(RELEASES_URL) {
            header(HttpHeaders.Accept, "application/vnd.github+json")
        }
        if (!response.status.isSuccess()) return null

        val currentVersion = VersionNumber.parse(currentVersionName) ?: return null
        return response.body<List<GitHubRelease>>()
            .asSequence()
            .filter { !it.draft && !it.prerelease }
            .mapNotNull { release ->
                val version = release.tagName.removePrefix("v")
                val apk = release.assets.firstOrNull { it.contentType == APK_CONTENT_TYPE }
                    ?: return@mapNotNull null
                val parsedVersion = VersionNumber.parse(version) ?: return@mapNotNull null
                if (parsedVersion <= currentVersion) return@mapNotNull null
                AppUpdate(
                    version = version,
                    releaseUrl = release.htmlUrl,
                    apkUrl = apk.downloadUrl,
                    apkFileName = apk.name,
                    apkSizeBytes = apk.size,
                    notes = release.body.orEmpty(),
                ) to parsedVersion
            }
            .maxByOrNull { it.second }
            ?.first
    }

    fun close() = client.close()
}

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val body: String? = null,
    val draft: Boolean,
    val prerelease: Boolean,
    val assets: List<GitHubAsset>,
)

@Serializable
private data class GitHubAsset(
    val name: String,
    val size: Long,
    @SerialName("content_type") val contentType: String,
    @SerialName("browser_download_url") val downloadUrl: String,
)

/** Numeric version comparison for release tags such as 1.6, v1.6.1, and 1.10. */
internal data class VersionNumber(private val parts: List<Int>) : Comparable<VersionNumber> {
    override fun compareTo(other: VersionNumber): Int {
        val length = maxOf(parts.size, other.parts.size)
        for (index in 0 until length) {
            val comparison = parts.getOrElse(index) { 0 }.compareTo(other.parts.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }

    companion object {
        fun parse(value: String): VersionNumber? {
            val numeric = value.trim().removePrefix("v")
            if (!numeric.matches(Regex("\\d+(\\.\\d+)*"))) return null
            return VersionNumber(numeric.split('.').map(String::toInt))
        }
    }
}

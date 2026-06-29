package org.akkirrai.animeresolver.extractor

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import org.akkirrai.animeresolver.core.PlayerExtractor
import org.akkirrai.animeresolver.core.SourceException
import org.akkirrai.animeresolver.model.PlayerLink
import org.akkirrai.animeresolver.model.PlayerType
import org.akkirrai.animeresolver.model.StreamType
import org.akkirrai.animeresolver.model.VideoStream
import org.akkirrai.animeresolver.network.bodyOrThrow
import org.akkirrai.animeresolver.network.hostOf
import org.akkirrai.animeresolver.network.normalizeUrl
import org.akkirrai.animeresolver.network.originOf
import org.akkirrai.animeresolver.network.pathOf

class AksorExtractor(
    private val client: HttpClient,
) : PlayerExtractor {
    override fun supports(link: PlayerLink): Boolean =
        link.type == PlayerType.EMBED && hostOf(link.url)?.endsWith("aksor.tv") == true

    override suspend fun extract(link: PlayerLink): VideoStream =
        extractVariants(link).first()

    override suspend fun extractVariants(link: PlayerLink): List<VideoStream> {
        val pageUrl = normalizeUrl(link.url)
        val videoId = VIDEO_ID.find(pathOf(pageUrl))?.groupValues?.get(1)
            ?: throw SourceException("Не удалось определить Aksor video id")
        val origin = originOf(pageUrl)
        val playbackHeaders = buildPlaybackHeaders(link.headers, pageUrl)

        val apiStreams = runCatching {
            val video = client.get("$origin/api/video/$videoId") {
                playbackHeaders.forEach { (name, value) -> header(name, value) }
                header(HttpHeaders.Accept, "application/json")
            }.bodyOrThrow<AksorVideo>("Aksor")
            video.qualities.toStreams(playbackHeaders)
        }.getOrDefault(emptyList())

        if (apiStreams.isNotEmpty()) {
            return apiStreams
        }

        val pageHtml = loadText(pageUrl, playbackHeaders, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        extractMetaVideoUrl(pageHtml)?.let { directUrl ->
            return listOf(
                VideoStream(
                    url = directUrl,
                    type = streamTypeFor(directUrl),
                    quality = qualityFromUrl(directUrl),
                    headers = playbackHeaders,
                ),
            )
        }

        val fallbackApiUrl = resolveFallbackApiUrl(pageHtml, pageUrl, origin, playbackHeaders)
        if (fallbackApiUrl != null) {
            val video = client.get(fallbackApiUrl) {
                playbackHeaders.forEach { (name, value) -> header(name, value) }
                header(HttpHeaders.Accept, "application/json")
            }.bodyOrThrow<AksorVideo>("Aksor")
            val fallbackStreams = video.qualities.toStreams(playbackHeaders)
            if (fallbackStreams.isNotEmpty()) {
                return fallbackStreams
            }
        }

        throw SourceException("Aksor не вернул доступных качеств")
    }

    private suspend fun resolveFallbackApiUrl(
        pageHtml: String,
        pageUrl: String,
        origin: String,
        headers: Map<String, String>,
    ): String? {
        val scriptUrls = SCRIPT_SRC.findAll(pageHtml)
            .map { match -> normalizeScriptUrl(match.groupValues[1], origin) }
            .filter(String::isNotBlank)
            .toList()

        return scriptUrls.firstNotNullOfOrNull { scriptUrl ->
            val script = runCatching { loadText(scriptUrl, headers, "*/*") }.getOrNull() ?: return@firstNotNullOfOrNull null
            val apiPath = API_PATH.find(script)?.groupValues?.getOrNull(1) ?: return@firstNotNullOfOrNull null
            when {
                apiPath.startsWith("http", ignoreCase = true) -> "$apiPath/video/${extractVideoId(pageUrl)}"
                apiPath.startsWith("/") -> "$origin$apiPath/video/${extractVideoId(pageUrl)}"
                else -> "$origin/$apiPath/video/${extractVideoId(pageUrl)}"
            }
        }
    }

    private suspend fun loadText(
        url: String,
        headers: Map<String, String>,
        accept: String,
    ): String {
        val response = client.get(url) {
            headers.forEach { (name, value) -> header(name, value) }
            header(HttpHeaders.Accept, accept)
        }
        if (!response.status.isSuccess()) {
            throw SourceException("Aksor вернул HTTP ${response.status.value}", response.status.value)
        }
        return response.bodyAsText()
    }

    private fun Map<String, String?>.toStreams(playbackHeaders: Map<String, String>): List<VideoStream> {
        return entries
            .mapNotNull { (key, url) ->
                val quality = qualityValue(key)
                val streamUrl = url?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
                    ?: return@mapNotNull null
                VideoStream(
                    url = streamUrl,
                    type = streamTypeFor(streamUrl),
                    quality = "${quality}p",
                    headers = playbackHeaders,
                )
            }
            .sortedByDescending { stream -> qualityValue(stream.quality.orEmpty()) }
            .distinctBy { stream -> stream.quality to stream.url }
    }

    private fun buildPlaybackHeaders(
        inputHeaders: Map<String, String>,
        pageUrl: String,
    ): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        inputHeaders.forEach { (name, value) ->
            if (name.isNotBlank() && value.isNotBlank()) {
                headers[name] = value
            }
        }
        headers.removeByName(HttpHeaders.Referrer)
        headers.removeByName("Referrer")
        headers.removeByName(HttpHeaders.UserAgent)
        headers[HttpHeaders.Referrer] = pageUrl
        headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
        return headers
    }

    private fun MutableMap<String, String>.removeByName(name: String) {
        keys.firstOrNull { it.equals(name, ignoreCase = true) }?.let(::remove)
    }

    private fun extractMetaVideoUrl(html: String): String? =
        META_VIDEO_URL.find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.contains("{{") }

    private fun normalizeScriptUrl(rawUrl: String, origin: String): String {
        val value = rawUrl.trim()
        return when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("/") -> "$origin$value"
            value.startsWith("http", ignoreCase = true) -> value
            else -> "$origin/$value"
        }
    }

    private fun extractVideoId(pageUrl: String): String {
        return VIDEO_ID.find(pathOf(pageUrl))?.groupValues?.get(1)
            ?: throw SourceException("Не удалось определить Aksor video id")
    }

    private fun qualityValue(key: String): Int =
        key.removePrefix("q").removeSuffix("p").let {
            when (it.lowercase()) {
                "2k" -> 1440
                "4k" -> 2160
                else -> it.toIntOrNull() ?: 0
            }
        }

    private fun qualityFromUrl(url: String): String? =
        QUALITY_FROM_URL.find(url)?.groupValues?.getOrNull(1)?.let { "${it}p" }

    private fun streamTypeFor(url: String): StreamType = when {
        url.substringBefore('?').endsWith(".mpd", ignoreCase = true) -> StreamType.DASH
        url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) -> StreamType.HLS
        else -> StreamType.MP4
    }

    private companion object {
        val VIDEO_ID = Regex("""/video/([^/]+)""")
        val META_VIDEO_URL = Regex("""<meta[^>]+name=["']video_url["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val SCRIPT_SRC = Regex("""<script[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val API_PATH = Regex("""["']([^"']*/api)["']""")
        val QUALITY_FROM_URL = Regex("""(?i)(\d{3,4})(?=p?(?:\.mp4|\.m3u8|\.mpd|/|$))""")
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
    }
}

@Serializable
private data class AksorVideo(
    val qualities: Map<String, String?>,
)

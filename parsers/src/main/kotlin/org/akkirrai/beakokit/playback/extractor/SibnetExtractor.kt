package org.akkirrai.beakokit.playback.extractor

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import org.akkirrai.beakokit.api.StreamExtractor
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.StreamType
import org.akkirrai.beakokit.model.VideoStream
import org.akkirrai.beakokit.http.hostOf
import org.akkirrai.beakokit.http.normalizeUrl
import org.akkirrai.beakokit.http.originOf

class SibnetExtractor(
    private val client: HttpClient,
) : StreamExtractor {
    override fun supports(link: PlayerLink): Boolean =
        link.type == PlayerType.EMBED && hostOf(link.url)?.endsWith("sibnet.ru") == true

    override suspend fun extract(link: PlayerLink): VideoStream =
        extractVariants(link).first()

    override suspend fun extractVariants(link: PlayerLink): List<VideoStream> {
        val pageUrl = normalizeUrl(link.url)
        val pageOrigin = originOf(pageUrl)
        val response = client.get(pageUrl) {
            link.headers.forEach { (name, value) -> header(name, value) }
            header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        }
        if (!response.status.isSuccess()) {
            throw SourceException("Sibnet embed вернул HTTP ${response.status.value}", response.status.value)
        }

        val html = response.bodyAsText()
        val playbackReferer = extractPlaybackReferer(html)?.let(::normalizeUrl) ?: pageUrl
        val sources = SOURCE.findAll(html)
            .mapNotNull { match ->
                val rawUrl = match.groupValues.getOrNull(1)?.trim().orEmpty()
                if (rawUrl.isBlank()) return@mapNotNull null

                val type = streamTypeFor(match.groupValues.getOrNull(2).orEmpty(), rawUrl)
                VideoStream(
                    url = absoluteUrl(pageOrigin, rawUrl),
                    type = type,
                    quality = link.quality,
                    headers = buildPlaybackHeaders(
                        baseHeaders = link.headers,
                        referer = playbackReferer,
                        origin = pageOrigin,
                    ),
                )
            }
            .distinctBy { stream -> stream.url to stream.type }
            .toList()

        return sources.ifEmpty {
            throw SourceException("Sibnet embed не содержит ссылок на поток")
        }
    }

    private fun absoluteUrl(origin: String, url: String): String = when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "$origin$url"
        else -> url
    }

    private fun streamTypeFor(contentType: String, url: String): StreamType = when {
        contentType.contains("mpegurl", ignoreCase = true) ||
            contentType.contains("m3u8", ignoreCase = true) ||
            url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) -> StreamType.HLS

        contentType.contains("dash", ignoreCase = true) ||
            contentType.contains("mpd", ignoreCase = true) ||
            url.substringBefore('?').endsWith(".mpd", ignoreCase = true) -> StreamType.DASH

        else -> StreamType.MP4
    }

    private fun extractPlaybackReferer(html: String): String? {
        return CANONICAL_URL.find(html)?.groupValues?.getOrNull(1)
            ?: OG_URL.find(html)?.groupValues?.getOrNull(1)
            ?: SHARE_URL.find(html)?.groupValues?.getOrNull(1)
    }

    private fun buildPlaybackHeaders(
        baseHeaders: Map<String, String>,
        referer: String,
        origin: String,
    ): Map<String, String> {
        val headers = LinkedHashMap(baseHeaders)
        headers.removeByName(HttpHeaders.Referrer)
        headers.removeByName("Referrer")
        headers.removeByName(HttpHeaders.Origin)
        headers[HttpHeaders.Referrer] = referer
        headers[HttpHeaders.Origin] = origin
        headers.putIfMissing(HttpHeaders.UserAgent, DEFAULT_USER_AGENT)
        headers[HttpHeaders.Accept] = "*/*"
        return headers
    }

    private fun MutableMap<String, String>.removeByName(name: String) {
        keys.firstOrNull { it.equals(name, ignoreCase = true) }?.let(::remove)
    }

    private fun MutableMap<String, String>.putIfMissing(
        name: String,
        value: String,
    ) {
        if (keys.none { it.equals(name, ignoreCase = true) } && value.isNotBlank()) {
            this[name] = value
        }
    }

    private companion object {
        val SOURCE = Regex("""src:\s*"([^"]+)"\s*,\s*type:\s*"([^"]+)"""")
        val CANONICAL_URL = Regex("""<link\s+rel="canonical"\s+href="([^"]+)"""", RegexOption.IGNORE_CASE)
        val OG_URL = Regex("""<meta\s+property="og:url"\s+content="([^"]+)"""", RegexOption.IGNORE_CASE)
        val SHARE_URL = Regex("""sharesibnet\(\{\s*"url":"([^"]+)"""", RegexOption.IGNORE_CASE)
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
    }
}

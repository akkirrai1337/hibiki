package org.akkirrai.animeresolver.extractor

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.akkirrai.animeresolver.core.PlayerExtractor
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.StreamType
import org.akkirrai.beakokit.model.VideoStream
import org.akkirrai.animeresolver.network.hostOf
import org.akkirrai.animeresolver.network.normalizeUrl
import org.akkirrai.animeresolver.network.pathOf
import java.net.URLDecoder
import java.net.URI

class VkExtractor(
    private val client: HttpClient,
) : PlayerExtractor {
    override fun supports(link: PlayerLink): Boolean {
        if (link.type != PlayerType.EMBED) return false
        val host = hostOf(link.url) ?: return false
        val path = pathOf(link.url)
        return (host.endsWith("yummyani.me") && path.contains("iframeVK.html", ignoreCase = true)) ||
            host.endsWith("vkvideo.ru") ||
            host.endsWith("vk.com")
    }

    override suspend fun extract(link: PlayerLink): VideoStream =
        extractVariants(link).first()

    override suspend fun extractVariants(link: PlayerLink): List<VideoStream> {
        val embedUrl = resolveEmbedUrl(link.url)
        val upstreamReferer = link.headers[HttpHeaders.Referrer] ?: DEFAULT_REFERER
        val response = client.get(embedUrl) {
            link.headers.forEach { (name, value) -> header(name, value) }
            header(HttpHeaders.Referrer, upstreamReferer)
            header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            header(HttpHeaders.UserAgent, DEFAULT_USER_AGENT)
        }
        if (!response.status.isSuccess()) {
            throw SourceException("VK embed вернул HTTP ${response.status.value}", response.status.value)
        }

        val html = response.bodyAsText()
        val files = extractFilesFromPrefetchCache(html)
        val streams = buildList<VideoStream> {
            if (files != null) {
                addStreamsFromFiles(
                    files = files,
                    upstreamReferer = upstreamReferer,
                    requestHeaders = link.headers,
                ).forEach(::add)
            } else {
                fallbackStreamsFromHtml(
                    html = html,
                    baseUrl = embedUrl,
                    upstreamReferer = upstreamReferer,
                    requestHeaders = link.headers,
                ).forEach(::add)
            }
        }.distinctBy { stream -> stream.url to stream.type }

        return streams.ifEmpty {
            throw SourceException("VK embed не вернул ссылок на поток")
        }
    }

    private fun addStreamsFromFiles(
        files: JsonObject,
        upstreamReferer: String,
        requestHeaders: Map<String, String>,
    ): List<VideoStream> {
        return buildList {
            files["hls_fmp4"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)?.let { url ->
                add(
                    VideoStream(
                        url = url,
                        type = StreamType.HLS,
                        quality = null,
                        headers = buildPlaybackHeaders(requestHeaders, upstreamReferer),
                    ),
                )
            }
            files["hls"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)?.let { url ->
                add(
                    VideoStream(
                        url = url,
                        type = StreamType.HLS,
                        quality = null,
                        headers = buildPlaybackHeaders(requestHeaders, upstreamReferer),
                    ),
                )
            }
            files["dash_sep"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)?.let { url ->
                add(
                    VideoStream(
                        url = url,
                        type = StreamType.DASH,
                        quality = null,
                        headers = buildPlaybackHeaders(requestHeaders, upstreamReferer),
                    ),
                )
            }
            files.entries
                .mapNotNull { (key, value) ->
                    if (!key.startsWith("mp4_")) return@mapNotNull null
                    val url = value.jsonPrimitive.contentOrNull?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                    val quality = key.removePrefix("mp4_").toIntOrNull() ?: return@mapNotNull null
                    VkMp4File(quality = quality, url = url)
                }
                .sortedByDescending(VkMp4File::quality)
                .forEach { item ->
                    add(
                        VideoStream(
                            url = item.url,
                            type = StreamType.MP4,
                            quality = "${item.quality}p",
                            headers = buildPlaybackHeaders(requestHeaders, upstreamReferer),
                        ),
                    )
                }
        }
    }

    private fun extractFilesFromPrefetchCache(html: String): JsonObject? {
        val cacheJson = API_PREFETCH_CACHE.find(html)?.groupValues?.getOrNull(1) ?: return null
        val root = Json.parseToJsonElement(cacheJson).jsonArray
        return root.firstNotNullOfOrNull { item ->
            val objectNode = item.jsonObject
            if (objectNode["method"]?.jsonPrimitive?.contentOrNull != "video.getEmbed") return@firstNotNullOfOrNull null
            objectNode.jsonObjectPath("response", "video", "files")
        }
    }

    private fun fallbackStreamsFromHtml(
        html: String,
        baseUrl: String,
        upstreamReferer: String,
        requestHeaders: Map<String, String>,
    ): List<VideoStream> {
        val candidates = linkedMapOf<String, String>()

        NAMED_STREAM_PATTERNS.forEach { (quality, pattern) ->
            pattern.findAll(html).forEach { match ->
                addCandidate(candidates, quality, normalizeCandidateUrl(match.groupValues[1], baseUrl))
            }
        }

        FILES_BLOCK_PATTERN.find(html)?.groupValues?.getOrNull(1)?.let { filesBlock ->
            val block = filesBlock.replace("\"trailer\"", "")
            FILES_KEY_VALUE_PATTERN.findAll(block).forEach { match ->
                addCandidate(
                    candidates,
                    normalizeQualityLabel(match.groupValues[1]),
                    normalizeCandidateUrl(match.groupValues[2], baseUrl),
                )
            }
        }

        FILES_KEY_VALUE_PATTERN.findAll(html).forEach { match ->
            addCandidate(
                candidates,
                normalizeQualityLabel(match.groupValues[1]),
                normalizeCandidateUrl(match.groupValues[2], baseUrl),
            )
        }

        STREAM_URL_PATTERNS.forEach { pattern ->
            pattern.findAll(html).forEach { match ->
                val raw = match.groupValues.getOrNull(1)?.takeIf(String::isNotBlank) ?: match.value
                addCandidate(candidates, qualityFromUrl(raw), normalizeCandidateUrl(raw, baseUrl))
            }
        }

        DATA_SRC_PATTERNS.forEach { pattern ->
            pattern.findAll(html).forEach { match ->
                val raw = match.groupValues.getOrNull(1)?.trim().orEmpty()
                addCandidate(candidates, qualityFromUrl(raw), normalizeCandidateUrl(raw, baseUrl))
            }
        }

        return orderQualityMap(candidates).entries.mapNotNull { (quality, url) ->
            val type = when {
                url.contains(".m3u8", ignoreCase = true) -> StreamType.HLS
                url.contains(".mpd", ignoreCase = true) -> StreamType.DASH
                quality != "auto" -> StreamType.MP4
                else -> null
            } ?: return@mapNotNull null

            VideoStream(
                url = url,
                type = type,
                quality = quality.takeUnless { it == "auto" },
                headers = buildPlaybackHeaders(requestHeaders, upstreamReferer),
            )
        }.sortedWith(
            compareByDescending<VideoStream> { it.type == StreamType.HLS }
                .thenByDescending { qualityValue(it.quality) ?: 0 },
        )
    }

    private fun addCandidate(
        candidates: MutableMap<String, String>,
        quality: String,
        url: String,
    ) {
        if (url.isBlank()) return
        val cleaned = normalizeEscapedUrl(url)
        if (!isStreamLike(cleaned, quality)) return
        if (candidates.any { (key, value) -> key != quality && value == cleaned }) return
        val current = candidates[quality]
        if (current == null || current.length < cleaned.length) {
            candidates[quality] = cleaned
        }
    }

    private fun orderQualityMap(raw: Map<String, String>): LinkedHashMap<String, String> {
        val ordered = LinkedHashMap<String, String>()
        QUALITY_KEYS.forEach { qualityKey ->
            raw[qualityKey]?.let { ordered[qualityKey] = it }
            if (qualityKey == "auto") return@forEach
            if (!ordered.containsKey(qualityKey)) {
                raw[qualityKey.removeSuffix("p")]?.let { ordered[qualityKey] = it }
            }
        }
        raw.forEach { (key, value) ->
            if (!ordered.containsKey(key)) ordered[key] = value
        }
        return ordered
    }

    private fun resolveEmbedUrl(rawUrl: String): String {
        val url = Url(normalizeUrl(rawUrl))
        val host = url.host.lowercase()
        val path = url.encodedPath

        if (host.endsWith("yummyani.me") && path.contains("iframeVK.html", ignoreCase = true)) {
            val rawId = url.parameters["id"]?.trim().orEmpty().removePrefix("video")
            return buildVideoExtUrl(rawId)
        }

        if (host.endsWith("vkvideo.ru")) {
            val rawId = VIDEO_ID_IN_PATH.find(path)?.groupValues?.drop(1)?.joinToString("_").orEmpty()
            return buildVideoExtUrl(rawId)
        }

        if (host.endsWith("vk.com") && path.contains("video_ext.php")) {
            val ownerId = url.parameters["oid"]?.trim().orEmpty()
            val videoId = url.parameters["id"]?.trim().orEmpty()
            return buildVideoExtUrl("${ownerId}_${videoId}")
        }

        throw SourceException("Неподдерживаемая VK ссылка: $rawUrl")
    }

    private fun buildVideoExtUrl(rawVideoId: String): String {
        val match = VIDEO_ID.find(rawVideoId)
            ?: throw SourceException("Не удалось определить owner_id и video_id для VK")
        val ownerId = match.groupValues[1]
        val videoId = match.groupValues[2]
        return URLBuilder("https://vk.com/video_ext.php").apply {
            parameters.append("oid", ownerId)
            parameters.append("id", videoId)
            parameters.append("js_api", "1")
            parameters.append("partner_name", "viqeo")
        }.buildString()
    }

    private fun normalizeCandidateUrl(
        rawUrl: String,
        baseUrl: String,
    ): String {
        val normalized = normalizeEscapedUrl(rawUrl.trim().trim('"', '\''))
        if (normalized.isBlank()) return ""
        return when {
            normalized.startsWith("//") -> "https:$normalized"
            normalized.startsWith("http://") -> normalized.replaceFirst("http://", "https://")
            normalized.startsWith("https://") -> normalized
            normalized.startsWith("/") -> {
                val origin = runCatching {
                    val base = URI(baseUrl)
                    "${base.scheme}://${base.host}"
                }.getOrDefault("https://ru.yummyani.me")
                "$origin$normalized"
            }
            else -> if (baseUrl.isNotBlank()) {
                runCatching { URI(baseUrl).resolve(normalized).toString() }.getOrDefault(normalized)
            } else {
                normalized
            }
        }
    }

    private fun decodeVkEscapes(value: String): String =
        value
            .replace("\\/", "/")
            .replace("\\u002f", "/")
            .replace("\\u002F", "/")
            .replace("\\u0026", "&")
            .replace("\\u002D", "-")
            .replace("\\u002d", "-")
            .replace("\\\\", "\\")

    private fun normalizeEscapedUrl(url: String): String =
        decodeUnicodeEscapes(url)
            .replace("\\u0026", "&")
            .replace("\\u002D", "-")
            .replace("\\u002d", "-")
            .replace("\\/", "/")

    private fun decodeUnicodeEscapes(text: String): String {
        if (!text.contains("\\u")) return text
        val output = StringBuilder()
        var index = 0
        while (index < text.length) {
            val current = text[index]
            if (current == '\\' && index + 5 < text.length && text[index + 1] == 'u') {
                val hex = text.substring(index + 2, index + 6)
                runCatching {
                    output.append(hex.toInt(16).toChar())
                    index += 6
                }.getOrElse {
                    output.append(current)
                    index += 1
                }
            } else {
                output.append(current)
                index += 1
            }
        }
        return output.toString()
            .replace("\\n", "\n")
            .replace("\\\"", "\"")
            .replace("\\'", "'")
            .replace("\\\\", "\\")
    }

    private fun qualityFromUrl(url: String): String {
        val value = QUALITY_FROM_URL.find(url)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return "auto"
        return if (value in KNOWN_QUALITIES) "${value}p" else "auto"
    }

    private fun normalizeQualityLabel(label: String): String {
        val normalized = label.trim().lowercase()
        if (normalized.isBlank() || normalized == "hls" || normalized == "hls_fmp4" || normalized == "url") {
            return "auto"
        }
        val quality = QUALITY_NUMBER.find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return if (quality != null && quality in KNOWN_QUALITIES) "${quality}p" else label
    }

    private fun isStreamLike(
        url: String,
        quality: String,
    ): Boolean {
        val lowered = url.lowercase()
        if (lowered.contains(".m3u8") || lowered.contains(".mp4") || lowered.contains(".mpd")) return true
        val knownVkCdn = lowered.contains("okcdn.ru") ||
            lowered.contains("vkuser") ||
            lowered.contains("userapi.com") ||
            lowered.contains("vkvd")
        return knownVkCdn && quality != "auto"
    }

    private fun qualityValue(label: String?): Int? =
        label?.let { QUALITY_NUMBER.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }

    private fun buildPlaybackHeaders(
        requestHeaders: Map<String, String>,
        upstreamReferer: String,
    ): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        requestHeaders.forEach { (name, value) ->
            if (name.isNotBlank() && value.isNotBlank()) {
                headers[name] = value
            }
        }
        headers.removeByName(HttpHeaders.Referrer)
        headers.removeByName("Referrer")
        headers.removeByName(HttpHeaders.UserAgent)
        headers.removeByName(HttpHeaders.Accept)
        headers[HttpHeaders.Referrer] = upstreamReferer
        headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
        headers[HttpHeaders.Accept] = "*/*"
        return headers
    }

    private fun MutableMap<String, String>.removeByName(name: String) {
        keys.firstOrNull { it.equals(name, ignoreCase = true) }?.let(::remove)
    }

    private fun JsonObject.jsonObjectPath(vararg keys: String): JsonObject? {
        var current: JsonElement = this
        for (key in keys) {
            current = current.jsonObject[key] ?: return null
        }
        return current as? JsonObject
    }

    private companion object {
        val QUALITY_KEYS = listOf("auto", "144p", "240p", "360p", "480p", "720p", "1080p", "1440p", "2160p")
        val API_PREFETCH_CACHE = Regex(
            """(?:window\.)?cur\.apiPrefetchCache\s*=\s*(\[[\s\S]+?]);""",
        )
        val NAMED_STREAM_PATTERNS = listOf(
            "1080p" to Regex("""(?i)\b(?:url1080|mp4_1080)\b\s*[:=]\s*['"]([^'"]+)['"]"""),
            "720p" to Regex("""(?i)\b(?:url720|mp4_720)\b\s*[:=]\s*['"]([^'"]+)['"]"""),
            "480p" to Regex("""(?i)\b(?:url480|mp4_480)\b\s*[:=]\s*['"]([^'"]+)['"]"""),
            "360p" to Regex("""(?i)\b(?:url360|mp4_360)\b\s*[:=]\s*['"]([^'"]+)['"]"""),
            "240p" to Regex("""(?i)\b(?:url240|mp4_240)\b\s*[:=]\s*['"]([^'"]+)['"]"""),
            "auto" to Regex("""(?i)\burl\b\s*[:=]\s*['"]([^'"]+)['"]"""),
            "auto" to Regex("""(?i)"hls_fmp4"\s*:\s*['"]([^'"]+)['"]"""),
            "auto" to Regex("""(?i)"hls"\s*:\s*['"]([^'"]+)['"]"""),
        )
        val FILES_BLOCK_PATTERN = Regex(
            """(?is)"files"\s*:\s*\{(.*?)\}\s*,\s*"trailer"""",
        )
        val FILES_KEY_VALUE_PATTERN =
            Regex("""(?i)"(mp4_\d{3,4}|hls_fmp4|hls|dash_sep|url\d{3,4}|url)"\s*:\s*['"]([^'"]+)['"]""")
        val STREAM_URL_PATTERNS = listOf(
            Regex("""(?i)https?:\\/\\/[^"'\\s]+\.(?:m3u8|mp4|mpd)[^"'\\s]*"""),
            Regex("""(?i)\\/\\/[^"'\\s]+\.(?:m3u8|mp4|mpd)[^"'\\s]*"""),
            Regex("""(?i)\b(?:videoUrl|fileList|file|src)\b[^=]*=\s*['"]([^'"]+\.(?:m3u8|mp4|mpd)[^'"]*)['"]"""),
        )
        val DATA_SRC_PATTERNS = listOf(
            Regex("""(?i)data-video(?:-src|Src)\s*=\s*['"]([^'"]+)['"]"""),
            Regex("""(?i)<source[^>]+src=['"]([^'"]+\.(?:m3u8|mp4|mpd)[^'"]*)['"]"""),
        )
        val VIDEO_ID = Regex("""(-?\d+)_(\d+)""")
        val VIDEO_ID_IN_PATH = Regex("""video(-?\d+)_(\d+)""")
        val QUALITY_FROM_URL = Regex("""(?i)(\d{3,4})(?=p?(?:\.mp4|/|$))""")
        val QUALITY_NUMBER = Regex("""(\d{3,4})""")
        val KNOWN_QUALITIES = setOf(144, 240, 360, 480, 720, 1080, 1440, 2160)
        const val DEFAULT_REFERER = "https://ru.yummyani.me/"
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
    }
}

private data class VkMp4File(
    val quality: Int,
    val url: String,
)

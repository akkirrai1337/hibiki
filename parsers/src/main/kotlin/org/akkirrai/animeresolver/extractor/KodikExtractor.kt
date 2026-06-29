package org.akkirrai.animeresolver.extractor

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.akkirrai.animeresolver.core.PlayerExtractor
import org.akkirrai.animeresolver.core.SourceException
import org.akkirrai.animeresolver.model.PlayerLink
import org.akkirrai.animeresolver.model.PlayerType
import org.akkirrai.animeresolver.model.StreamType
import org.akkirrai.animeresolver.model.VideoSegment
import org.akkirrai.animeresolver.model.VideoSegmentType
import org.akkirrai.animeresolver.model.VideoStream
import org.akkirrai.animeresolver.network.bodyOrThrow
import org.akkirrai.animeresolver.network.decodeShiftedBase64
import org.akkirrai.animeresolver.network.hostOf
import org.akkirrai.animeresolver.network.normalizeUrl
import org.akkirrai.animeresolver.network.originOf
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64

class KodikExtractor(
    private val client: HttpClient,
) : PlayerExtractor {
    override fun supports(link: PlayerLink): Boolean {
        if (link.type != PlayerType.EMBED) return false
        val host = hostOf(link.url)?.lowercase().orEmpty()
        return host.contains("kodik")
    }

    override suspend fun extract(link: PlayerLink): VideoStream =
        extractVariants(link).first()

    override suspend fun extractVariants(link: PlayerLink): List<VideoStream> {
        val pageUrl = normalizeUrl(link.url)
        val pageOrigin = originOf(pageUrl)
        val page = loadPage(pageUrl, link.headers)
        val pageInfo = parsePageInfo(page.html)
        val endpointUrl = resolveEndpointUrl(page.html, pageUrl, pageOrigin, link.headers)
        val segments = parseSkipSegments(page.html)

        val response = client.post(endpointUrl) {
            link.headers.forEach { (name, value) -> header(name, value) }
            header(HttpHeaders.Accept, "application/json, text/javascript, */*; q=0.01")
            header(HttpHeaders.Origin, pageOrigin)
            header(HttpHeaders.Referrer, pageUrl)
            header("X-Requested-With", "XMLHttpRequest")
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            if (page.cookies.isNotBlank()) {
                header(HttpHeaders.Cookie, page.cookies)
            }
            setBody(FormDataContent(Parameters.build {
                appendRequiredUrlParams(pageInfo.urlParams)
                append("bad_user", "false")
                append("cdn_is_working", "false")
                append("type", pageInfo.type)
                append("hash", pageInfo.hash)
                append("id", pageInfo.videoId)
                append("info", "{}")
            }))
        }

        val ftor = response.bodyOrThrow<KodikFtorResponse>("Kodik")
        val candidates = ftor.links.entries.flatMap { (quality, items) ->
            items.mapNotNull { item ->
                val source = item.src?.takeIf(String::isNotBlank)?.let(::decodeSource) ?: return@mapNotNull null
                qualityValue(quality)?.let { numericQuality ->
                    VideoStream(
                        url = repairManifestQuality(source, expectedQuality = numericQuality),
                        type = streamTypeFor(item, source),
                        quality = "${numericQuality}p",
                        headers = buildPlaybackHeaders(link.headers, pageUrl),
                        segments = segments,
                    )
                }
            }
        }.distinctBy { stream -> stream.quality to stream.url }
            .sortedByDescending { stream -> qualityValue(stream.quality.orEmpty()) ?: 0 }

        return candidates.ifEmpty {
            throw SourceException("Kodik не вернул доступных качеств")
        }
    }

    private suspend fun loadPage(url: String, headers: Map<String, String>): KodikPage {
        val response = client.get(url) {
            headers.forEach { (name, value) -> header(name, value) }
            header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        }
        if (!response.status.isSuccess()) {
            throw SourceException("Kodik вернул HTTP ${response.status.value}", response.status.value)
        }
        return KodikPage(
            html = response.bodyAsText(),
            cookies = response.headers.getAll(HttpHeaders.SetCookie)
                ?.joinToString("; ") { it.substringBefore(';') }
                .orEmpty(),
        )
    }

    private suspend fun resolveEndpointUrl(
        html: String,
        pageUrl: String,
        pageOrigin: String,
        headers: Map<String, String>,
    ): String {
        val playerScriptUrl = PLAYER_SCRIPT.find(html)?.groupValues?.getOrNull(1)
            ?.let { normalizeScriptUrl(it, pageOrigin) }
            ?: return "$pageOrigin/ftor"
        val script = runCatching {
            val response = client.get(playerScriptUrl) {
                headers.forEach { (name, value) -> header(name, value) }
                header(HttpHeaders.Referrer, pageUrl)
                header(HttpHeaders.Accept, "*/*")
            }
            if (!response.status.isSuccess()) return@runCatching null
            response.bodyAsText()
        }.getOrNull() ?: return "$pageOrigin/ftor"

        val endpointPath = ATOB_ENDPOINT.findAll(script)
            .mapNotNull { match ->
                runCatching {
                    String(Base64.getDecoder().decode(match.groupValues[1]))
                }.getOrNull()
            }
            .firstOrNull { decoded ->
                decoded.startsWith("/") && !decoded.startsWith("//") && decoded.length <= 12
            }
            ?: return "$pageOrigin/ftor"

        return "$pageOrigin$endpointPath"
    }

    private fun parsePageInfo(html: String): KodikPageInfo {
        val urlParamsJson = findRequired(URL_PARAMS, html, "urlParams")
        val videoId = findRequired(VIDEO_ID, html, "videoId")
        val type = findRequired(TYPE, html, "type")
        val hash = findRequired(HASH, html, "hash")
        val urlParams = JSON.parseToJsonElement(urlParamsJson)
            .jsonObject
            .mapValues { (key, value) ->
                val raw = value.jsonPrimitive.content
                if (key == "ref") raw.decodeUrlParamIfNeeded() else raw
            }

        return KodikPageInfo(
            videoId = videoId,
            type = type,
            hash = hash,
            urlParams = urlParams,
        )
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
        headers[HttpHeaders.Referrer] = pageUrl
        return headers
    }

    private fun MutableMap<String, String>.removeByName(name: String) {
        keys.firstOrNull { it.equals(name, ignoreCase = true) }?.let(::remove)
    }

    private fun findRequired(regex: Regex, text: String, label: String): String =
        regex.find(text)?.groupValues?.get(1)
            ?: throw SourceException("Kodik не смог прочитать $label")

    private fun qualityValue(quality: String): Int? =
        quality.filter(Char::isDigit).toIntOrNull()

    private fun io.ktor.http.ParametersBuilder.appendRequiredUrlParams(urlParams: Map<String, String>) {
        val missing = REQUIRED_URL_PARAM_KEYS.filterNot(urlParams::containsKey)
        if (missing.isNotEmpty()) {
            throw SourceException("Kodik ne smog nayti obyazatel'nye parametry urlParams: ${missing.joinToString(", ")}")
        }
        REQUIRED_URL_PARAM_KEYS.forEach { key ->
            append(key, urlParams.getValue(key))
        }
    }

    private fun decodeSource(raw: String): String =
        decodeShiftedBase64(raw)

    private fun repairManifestQuality(url: String, expectedQuality: Int): String {
        val match = HLS_QUALITY_MANIFEST.find(url) ?: return url
        val actualQuality = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return url
        return if (actualQuality >= expectedQuality) url else {
            url.replaceRange(match.range, "/$expectedQuality.mp4:hls:manifest.m3u8")
        }
    }

    private fun parseSkipSegments(html: String): List<VideoSegment> {
        return SKIP_BUTTON.findAll(html)
            .mapNotNull { match ->
                val range = match.groupValues.getOrNull(1).orEmpty()
                val kind = match.groupValues.getOrNull(2).orEmpty()
                val parts = range.split('-', limit = 2)
                if (parts.size != 2) return@mapNotNull null

                val startMs = parts[0].parseTimecodeMs() ?: return@mapNotNull null
                val endMs = parts[1].parseTimecodeMs() ?: return@mapNotNull null
                if (endMs <= startMs) return@mapNotNull null

                VideoSegment(
                    type = kind.toVideoSegmentType(),
                    startMs = startMs,
                    endMs = endMs,
                )
            }
            .distinctBy { segment -> segment.type to segment.startMs to segment.endMs }
            .toList()
    }

    private fun streamTypeFor(item: KodikFtorLink, url: String): StreamType =
        when {
            item.type?.contains("mpegurl", ignoreCase = true) == true ||
                item.type?.contains("m3u8", ignoreCase = true) == true ||
                url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) -> StreamType.HLS

            item.type?.contains("mpd", ignoreCase = true) == true ||
                url.substringBefore('?').endsWith(".mpd", ignoreCase = true) -> StreamType.DASH

            else -> StreamType.MP4
        }

    private fun normalizeScriptUrl(scriptUrl: String, pageOrigin: String): String = when {
        scriptUrl.startsWith("//") -> "https:$scriptUrl"
        scriptUrl.startsWith("/") -> "$pageOrigin$scriptUrl"
        scriptUrl.startsWith("http", ignoreCase = true) -> scriptUrl
        else -> "$pageOrigin/$scriptUrl"
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val URL_PARAMS = Regex("""\burlParams\s*=\s*'([^']+)'""")
        val VIDEO_ID = Regex("""\b(?:var\s+videoId|(?:videoInfo|vInfo)\.id)\s*=\s*["']([^"']+)["']""")
        val TYPE = Regex("""\b(?:var\s+type|(?:videoInfo|vInfo)\.type)\s*=\s*["']([^"']+)["']""")
        val HASH = Regex("""\b(?:vInfo|videoInfo)\.hash\s*=\s*["']([^"']+)["']""")
        val SKIP_BUTTON = Regex("""parseSkipButton\(\s*["']([^"']+)["']\s*,\s*["']([^"']*)["']\s*\)""")
        val REQUIRED_URL_PARAM_KEYS = listOf("d", "d_sign", "pd", "pd_sign", "ref", "ref_sign")
        val PLAYER_SCRIPT = Regex("""src=["']((?://[^"']+)?/assets/js/app\.player_single[^"']+)["']""", RegexOption.IGNORE_CASE)
        val ATOB_ENDPOINT = Regex("""atob\("([A-Za-z0-9+/=]+)"\)""")
        val HLS_QUALITY_MANIFEST = Regex("""/(\d+)\.mp4:hls:manifest\.m3u8(?=$|[?#])""")
    }
}

private fun String.decodeUrlParamIfNeeded(): String {
    if ('%' !in this && '+' !in this) return this
    return runCatching {
        URLDecoder.decode(this, StandardCharsets.UTF_8)
    }.getOrDefault(this)
}

private fun String.parseTimecodeMs(): Long? {
    val parts = trim().split(':')
    if (parts.isEmpty() || parts.size > 3) return null

    var multiplier = 1_000L
    var totalMs = 0L
    for (part in parts.asReversed()) {
        val value = part.toLongOrNull() ?: return null
        totalMs += value * multiplier
        multiplier *= 60
    }
    return totalMs
}

private fun String.toVideoSegmentType(): VideoSegmentType =
    when (trim().lowercase()) {
        "opening", "op", "intro" -> VideoSegmentType.OPENING
        "ending", "ed", "outro" -> VideoSegmentType.ENDING
        "anime" -> VideoSegmentType.OPENING
        else -> VideoSegmentType.UNKNOWN
    }

private data class KodikPage(
    val html: String,
    val cookies: String,
)

private data class KodikPageInfo(
    val videoId: String,
    val type: String,
    val hash: String,
    val urlParams: Map<String, String>,
)

@Serializable
private data class KodikFtorResponse(
    val default: Int? = null,
    val links: Map<String, List<KodikFtorLink>> = emptyMap(),
)

@Serializable
private data class KodikFtorLink(
    val src: String? = null,
    val type: String? = null,
)

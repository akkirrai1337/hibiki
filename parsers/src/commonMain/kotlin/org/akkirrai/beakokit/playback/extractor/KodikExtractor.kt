package org.akkirrai.beakokit.playback.extractor

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.decodeURLPart
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.beakokit.api.StreamExtractor
import org.akkirrai.beakokit.http.decodeShiftedBase64
import org.akkirrai.beakokit.http.hostOf
import org.akkirrai.beakokit.http.normalizeUrl
import org.akkirrai.beakokit.http.originOf
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.StreamType
import org.akkirrai.beakokit.model.VideoSegment
import org.akkirrai.beakokit.model.VideoSegmentType
import org.akkirrai.beakokit.model.VideoStream
import org.akkirrai.beakokit.http.bodyOrThrow
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Resolves Kodik embeds returned by YummyAnime into native media streams. */
class KodikExtractor(
    private val client: HttpClient,
) : StreamExtractor {
    override fun supports(link: PlayerLink): Boolean =
        link.type == PlayerType.EMBED && hostOf(link.url)?.lowercase()?.contains("kodik") == true

    override suspend fun extract(link: PlayerLink): VideoStream = extractVariants(link).first()

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
            if (page.cookies.isNotBlank()) header(HttpHeaders.Cookie, page.cookies)
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
                val numericQuality = qualityValue(quality) ?: return@mapNotNull null
                VideoStream(
                    url = repairManifestQuality(source, numericQuality),
                    type = streamTypeFor(item, source),
                    quality = "${numericQuality}p",
                    headers = buildPlaybackHeaders(link.headers, pageUrl),
                    segments = segments,
                )
            }
        }.distinctBy { it.quality to it.url }
            .sortedByDescending { qualityValue(it.quality.orEmpty()) ?: 0 }

        return candidates.ifEmpty { throw SourceException("Kodik не вернул доступных качеств") }
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
                ?.joinToString("; ") { it.substringBefore(';') }.orEmpty(),
        )
    }

    private suspend fun resolveEndpointUrl(html: String, pageUrl: String, pageOrigin: String, headers: Map<String, String>): String {
        val scriptUrl = PLAYER_SCRIPT.find(html)?.groupValues?.getOrNull(1)?.let { normalizeScriptUrl(it, pageOrigin) }
            ?: return "$pageOrigin/ftor"
        val script = runCatching {
            val response = client.get(scriptUrl) {
                headers.forEach { (name, value) -> header(name, value) }
                header(HttpHeaders.Referrer, pageUrl)
                header(HttpHeaders.Accept, "*/*")
            }
            if (!response.status.isSuccess()) null else response.bodyAsText()
        }.getOrNull() ?: return "$pageOrigin/ftor"
        val endpointPath = ATOB_ENDPOINT.findAll(script).mapNotNull { match ->
            decodeBase64(match.groupValues[1])
        }.firstOrNull { it.startsWith('/') && !it.startsWith("//") && it.length <= 12 }
        return if (endpointPath == null) "$pageOrigin/ftor" else "$pageOrigin$endpointPath"
    }

    private fun parsePageInfo(html: String): KodikPageInfo {
        val urlParamsJson = findRequired(URL_PARAMS, html, "urlParams")
        val urlParams = JSON.parseToJsonElement(urlParamsJson).jsonObject.mapValues { (key, value) ->
            val raw = value.jsonPrimitive.content
            if (key == "ref") raw.decodeURLPart() else raw
        }
        return KodikPageInfo(
            videoId = findRequired(VIDEO_ID, html, "videoId"),
            type = findRequired(TYPE, html, "type"),
            hash = findRequired(HASH, html, "hash"),
            urlParams = urlParams,
        )
    }

    private fun buildPlaybackHeaders(input: Map<String, String>, pageUrl: String): Map<String, String> =
        LinkedHashMap<String, String>().apply {
            input.forEach { (name, value) -> if (name.isNotBlank() && value.isNotBlank()) put(name, value) }
            keys.firstOrNull { it.equals(HttpHeaders.Referrer, true) }?.let(::remove)
            keys.firstOrNull { it.equals("Referrer", true) }?.let(::remove)
            put(HttpHeaders.Referrer, pageUrl)
        }

    private fun findRequired(regex: Regex, text: String, label: String): String =
        regex.find(text)?.groupValues?.get(1) ?: throw SourceException("Kodik не смог прочитать $label")

    private fun io.ktor.http.ParametersBuilder.appendRequiredUrlParams(urlParams: Map<String, String>) {
        val missing = REQUIRED_URL_PARAM_KEYS.filterNot(urlParams::containsKey)
        if (missing.isNotEmpty()) {
            throw SourceException("Kodik ne smog nayti obyazatel'nye parametry urlParams: ${missing.joinToString(", ")}")
        }
        REQUIRED_URL_PARAM_KEYS.forEach { append(it, urlParams.getValue(it)) }
    }

    private fun decodeSource(raw: String): String = decodeShiftedBase64(raw)

    private fun repairManifestQuality(url: String, expectedQuality: Int): String {
        val match = HLS_QUALITY_MANIFEST.find(url) ?: return url
        val actualQuality = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return url
        return if (actualQuality >= expectedQuality) url else url.replaceRange(match.range, "/$expectedQuality.mp4:hls:manifest.m3u8")
    }

    private fun parseSkipSegments(html: String): List<VideoSegment> = SKIP_BUTTON.findAll(html).mapNotNull { match ->
        val parts = match.groupValues.getOrNull(1).orEmpty().split('-', limit = 2)
        if (parts.size != 2) return@mapNotNull null
        val startMs = parts[0].parseTimecodeMs() ?: return@mapNotNull null
        val endMs = parts[1].parseTimecodeMs() ?: return@mapNotNull null
        if (endMs <= startMs) return@mapNotNull null
        VideoSegment(match.groupValues.getOrNull(2).orEmpty().toVideoSegmentType(), startMs, endMs)
    }.distinctBy { it.type to it.startMs to it.endMs }.toList()

    private fun streamTypeFor(item: KodikFtorLink, url: String): StreamType = when {
        item.type?.contains("mpegurl", true) == true || item.type?.contains("m3u8", true) == true || url.substringBefore('?').endsWith(".m3u8", true) -> StreamType.HLS
        item.type?.contains("mpd", true) == true || url.substringBefore('?').endsWith(".mpd", true) -> StreamType.DASH
        else -> StreamType.MP4
    }

    private fun normalizeScriptUrl(value: String, origin: String): String = when {
        value.startsWith("//") -> "https:$value"
        value.startsWith("/") -> "$origin$value"
        value.startsWith("http", true) -> value
        else -> "$origin/$value"
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeBase64(value: String): String = runCatching { Base64.Default.decode(value).decodeToString() }.getOrNull().orEmpty()

    private fun qualityValue(value: String): Int? = value.filter(Char::isDigit).toIntOrNull()

    private fun String.parseTimecodeMs(): Long? {
        val parts = trim().split(':')
        if (parts.isEmpty() || parts.size > 3) return null
        var multiplier = 1_000L
        var total = 0L
        for (part in parts.asReversed()) {
            total += (part.toLongOrNull() ?: return null) * multiplier
            multiplier *= 60
        }
        return total
    }

    private fun String.toVideoSegmentType(): VideoSegmentType = when (trim().lowercase()) {
        "opening", "op", "intro", "anime" -> VideoSegmentType.OPENING
        "ending", "ed", "outro" -> VideoSegmentType.ENDING
        else -> VideoSegmentType.UNKNOWN
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val URL_PARAMS = Regex("""\burlParams\s*=\s*'([^']+)'""")
        val VIDEO_ID = Regex("""\b(?:var\s+videoId|(?:videoInfo|vInfo)\.id)\s*=\s*[\"']([^\"']+)[\"']""")
        val TYPE = Regex("""\b(?:var\s+type|(?:videoInfo|vInfo)\.type)\s*=\s*[\"']([^\"']+)[\"']""")
        val HASH = Regex("""\b(?:vInfo|videoInfo)\.hash\s*=\s*[\"']([^\"']+)[\"']""")
        val SKIP_BUTTON = Regex("""parseSkipButton\(\s*[\"']([^\"']+)[\"']\s*,\s*[\"']([^\"']*)[\"']\s*\)""")
        val REQUIRED_URL_PARAM_KEYS = listOf("d", "d_sign", "pd", "pd_sign", "ref", "ref_sign")
        val PLAYER_SCRIPT = Regex("""src=[\"']((?://[^\"']+)?/assets/js/app\.player_single[^\"']+)[\"']""", RegexOption.IGNORE_CASE)
        val ATOB_ENDPOINT = Regex("""atob\(\"([A-Za-z0-9+/=]+)\"\)""")
        val HLS_QUALITY_MANIFEST = Regex("""/(\d+)\.mp4:hls:manifest\.m3u8(?=$|[?#])""")
    }
}

private data class KodikPage(val html: String, val cookies: String)
private data class KodikPageInfo(val videoId: String, val type: String, val hash: String, val urlParams: Map<String, String>)

@Serializable
private data class KodikFtorResponse(
    val default: Int? = null,
    val links: Map<String, List<KodikFtorLink>> = emptyMap(),
)

@Serializable
private data class KodikFtorLink(val src: String? = null, val type: String? = null)

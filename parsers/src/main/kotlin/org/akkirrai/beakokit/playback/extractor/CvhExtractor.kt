package org.akkirrai.beakokit.playback.extractor

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.akkirrai.beakokit.api.StreamExtractor
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.StreamType
import org.akkirrai.beakokit.model.VideoStream
import org.akkirrai.beakokit.http.bodyOrThrow
import org.akkirrai.beakokit.http.hostOf
import org.akkirrai.beakokit.http.normalizeUrl
import org.akkirrai.beakokit.http.pathOf

class CvhExtractor(
    private val client: HttpClient,
) : StreamExtractor {
    override fun supports(link: PlayerLink): Boolean =
        link.type == PlayerType.EMBED &&
            hostOf(link.url)?.endsWith("yummyani.me") == true &&
            pathOf(link.url).contains("iframeCVH.html", ignoreCase = true)

    override suspend fun extract(link: PlayerLink): VideoStream =
        extractVariants(link).first()

    override suspend fun extractVariants(link: PlayerLink): List<VideoStream> {
        val pageUrl = normalizeUrl(link.url)
        val page = Url(pageUrl)
        val animeId = page.parameters["anime_id"]?.trim().orEmpty()
        if (animeId.isBlank()) {
            throw SourceException("Не удалось определить anime_id для CVH")
        }

        val episode = page.parameters["episode"]?.toIntOrNull()
        val dubbingCode = page.parameters["dubbing_code"]?.normalizeToken()
        val dubbingLabel = page.parameters["dubbing"]?.normalizeToken()
        val referer = link.headers[HttpHeaders.Referrer] ?: DEFAULT_REFERER

        val playlist = client.get("$API_BASE/player/sv/playlist") {
            link.headers.forEach { (name, value) -> header(name, value) }
            header(HttpHeaders.Referrer, referer)
            header(HttpHeaders.Accept, "application/json")
            parameter("pub", PUBLISHER_ID)
            parameter("id", animeId)
            parameter("aggr", AGGREGATOR)
        }.bodyOrThrow<CvhPlaylistResponse>("CVH")

        val selected = playlist.items
            .asSequence()
            .filter { item -> episode == null || item.episode == null || item.episode == episode }
            .sortedByDescending { item -> score(item, dubbingCode, dubbingLabel) }
            .firstOrNull()
            ?: throw SourceException("CVH не вернул подходящее видео для anime_id=$animeId")

        val vkId = selected.vkId
            ?: throw SourceException("CVH не вернул vkId для выбранного видео")

        val video = client.get("$API_BASE/player/sv/video/$vkId") {
            link.headers.forEach { (name, value) -> header(name, value) }
            header(HttpHeaders.Referrer, referer)
            header(HttpHeaders.Accept, "application/json")
        }.bodyOrThrow<CvhVideoResponse>("CVH")

        val sources = listOfNotNull(
            video.sources?.dashUrl?.takeIf(String::isNotBlank)?.let { url ->
                VideoStream(
                    url = normalizeVideoUrl(url, video.failoverHost),
                    type = StreamType.DASH,
                    quality = null,
                    headers = link.headers + (HttpHeaders.Referrer to referer),
                )
            },
            video.sources?.hlsUrl?.takeIf(String::isNotBlank)?.let { url ->
                VideoStream(
                    url = normalizeVideoUrl(url, video.failoverHost),
                    type = StreamType.HLS,
                    quality = null,
                    headers = link.headers + (HttpHeaders.Referrer to referer),
                )
            },
            video.sources?.mpegFullHdUrl?.takeIf(String::isNotBlank)?.let { url ->
                VideoStream(
                    url = normalizeVideoUrl(url, video.failoverHost),
                    type = StreamType.MP4,
                    quality = "1080p",
                    headers = link.headers + (HttpHeaders.Referrer to referer),
                )
            },
            video.sources?.mpegHighUrl?.takeIf(String::isNotBlank)?.let { url ->
                VideoStream(
                    url = normalizeVideoUrl(url, video.failoverHost),
                    type = StreamType.MP4,
                    quality = "720p",
                    headers = link.headers + (HttpHeaders.Referrer to referer),
                )
            },
            video.sources?.mpegMediumUrl?.takeIf(String::isNotBlank)?.let { url ->
                VideoStream(
                    url = normalizeVideoUrl(url, video.failoverHost),
                    type = StreamType.MP4,
                    quality = "480p",
                    headers = link.headers + (HttpHeaders.Referrer to referer),
                )
            },
            video.sources?.mpegLowUrl?.takeIf(String::isNotBlank)?.let { url ->
                VideoStream(
                    url = normalizeVideoUrl(url, video.failoverHost),
                    type = StreamType.MP4,
                    quality = "360p",
                    headers = link.headers + (HttpHeaders.Referrer to referer),
                )
            },
            video.sources?.mpegLowestUrl?.takeIf(String::isNotBlank)?.let { url ->
                VideoStream(
                    url = normalizeVideoUrl(url, video.failoverHost),
                    type = StreamType.MP4,
                    quality = "240p",
                    headers = link.headers + (HttpHeaders.Referrer to referer),
                )
            },
        ).distinctBy { stream -> stream.url to stream.type }

        return sources.ifEmpty {
            throw SourceException("CVH не вернул ссылок на поток")
        }
    }

    private fun score(
        item: CvhPlaylistItem,
        dubbingCode: String?,
        dubbingLabel: String?,
    ): Int {
        val voiceStudio = item.voiceStudio.normalizeToken()
        val voiceType = item.voiceType.normalizeToken()

        return when {
            dubbingCode != null && voiceStudio == dubbingCode -> 4
            dubbingCode != null && voiceType == dubbingCode -> 3
            dubbingLabel != null && voiceStudio == dubbingLabel -> 2
            dubbingLabel != null && voiceType == dubbingLabel -> 1
            else -> 0
        }
    }

    private fun normalizeVideoUrl(
        url: String,
        failoverHost: String?,
    ): String {
        val normalized = normalizeUrl(url)
        val host = failoverHost?.takeIf(String::isNotBlank) ?: return normalized
        val parsed = runCatching { Url(normalized) }.getOrNull() ?: return normalized
        if (!parsed.protocol.name.equals("https", ignoreCase = true)) return normalized
        if (!IPV4_HOST.matches(parsed.host)) return normalized
        return normalized.replaceFirst(parsed.host, host)
    }

    private fun String?.normalizeToken(): String? =
        this?.trim()
            ?.lowercase()
            ?.replace('+', ' ')
            ?.replace(Regex("""\s+"""), " ")
            ?.takeIf(String::isNotBlank)

    private companion object {
        const val API_BASE = "https://plapi.cdnvideohub.com/api/v1"
        const val PUBLISHER_ID = 745
        const val AGGREGATOR = "mali"
        const val DEFAULT_REFERER = "https://ru.yummyani.me/"
        val IPV4_HOST = Regex("""\d{1,3}(?:\.\d{1,3}){3}""")
    }
}

@Serializable
private data class CvhPlaylistResponse(
    val items: List<CvhPlaylistItem> = emptyList(),
)

@Serializable
private data class CvhPlaylistItem(
    val episode: Int? = null,
    @SerialName("vkId") val vkId: Long? = null,
    @SerialName("voiceStudio") val voiceStudio: String? = null,
    @SerialName("voiceType") val voiceType: String? = null,
)

@Serializable
private data class CvhVideoResponse(
    val failoverHost: String? = null,
    val sources: CvhVideoSources? = null,
)

@Serializable
private data class CvhVideoSources(
    @SerialName("hlsUrl") val hlsUrl: String? = null,
    @SerialName("dashUrl") val dashUrl: String? = null,
    @SerialName("mpegLowestUrl") val mpegLowestUrl: String? = null,
    @SerialName("mpegLowUrl") val mpegLowUrl: String? = null,
    @SerialName("mpegMediumUrl") val mpegMediumUrl: String? = null,
    @SerialName("mpegHighUrl") val mpegHighUrl: String? = null,
    @SerialName("mpegFullHdUrl") val mpegFullHdUrl: String? = null,
)

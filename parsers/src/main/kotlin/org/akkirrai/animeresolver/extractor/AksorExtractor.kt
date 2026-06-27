package org.akkirrai.animeresolver.extractor

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
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

    override suspend fun extract(link: PlayerLink): VideoStream {
        val pageUrl = normalizeUrl(link.url)
        val id = VIDEO_ID.find(pathOf(pageUrl))?.groupValues?.get(1)
            ?: throw SourceException("Не удалось определить Aksor video id")
        val origin = originOf(pageUrl)
        val response = client.get("$origin/api/video/$id") {
            link.headers.forEach { (name, value) -> header(name, value) }
            header(HttpHeaders.Referrer, pageUrl)
            header("Accept", "application/json")
        }
        val video = response.bodyOrThrow<AksorVideo>("Aksor")
        val selected = video.qualities.entries
            .mapNotNull { (key, url) ->
                url?.takeIf(String::isNotBlank)?.let { qualityValue(key) to it }
            }
            .maxByOrNull { it.first }
            ?: throw SourceException("Aksor не вернул доступных качеств")

        return VideoStream(
            url = selected.second,
            type = when {
                selected.second.substringBefore('?').endsWith(".mpd") -> StreamType.DASH
                selected.second.substringBefore('?').endsWith(".m3u8") -> StreamType.HLS
                else -> StreamType.MP4
            },
            quality = "${selected.first}p",
            headers = link.headers + ("Referer" to pageUrl),
        )
    }

    private fun qualityValue(key: String): Int =
        key.removePrefix("q").let {
            when (it) {
                "2k" -> 1440
                "4k" -> 2160
                else -> it.toIntOrNull() ?: 0
            }
        }

    private companion object {
        val VIDEO_ID = Regex("""/video/([^/]+)""")
    }
}

@Serializable
private data class AksorVideo(
    val qualities: Map<String, String?>,
)

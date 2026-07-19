package org.akkirrai.animeresolver.extractor

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import org.akkirrai.animeresolver.core.PlayerExtractor
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.StreamType
import org.akkirrai.beakokit.model.VideoStream
import org.jsoup.parser.Parser

class AniBoomExtractor(
    private val client: HttpClient,
) : PlayerExtractor {
    override fun supports(link: PlayerLink): Boolean =
        link.type == PlayerType.EMBED && link.url.contains("aniboom.one")

    override suspend fun extract(link: PlayerLink): VideoStream {
        val response = client.get(link.url) {
            link.headers.forEach { (name, value) -> header(name, value) }
        }
        if (!response.status.isSuccess()) {
            throw SourceException(
                "AniBoom embed вернул HTTP ${response.status.value}",
                response.status.value,
            )
        }
        val html = Parser.unescapeEntities(response.bodyAsText(), false)
        val escapedUrl = HLS_URL.find(html)?.value
            ?: throw SourceException("AniBoom embed не содержит HLS URL")
        val hlsUrl = escapedUrl.replace("\\", "")
        val quality = QUALITY.find(html)?.groupValues?.get(1)?.let { "${it}p" }

        return VideoStream(
            url = hlsUrl,
            type = StreamType.HLS,
            quality = quality ?: link.quality,
            headers = link.headers + ("Referer" to link.url),
        )
    }

    private companion object {
        val HLS_URL = Regex("""https:[^"\s]+?\.m3u8(?:\?[^"\s\\]*)?""")
        val QUALITY = Regex(""""qualityVideo"\s*:\s*(\d+)""")
    }
}

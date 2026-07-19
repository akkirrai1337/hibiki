package org.akkirrai.beakokit.playback.validation

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import org.akkirrai.beakokit.api.StreamValidator
import org.akkirrai.beakokit.model.StreamType
import org.akkirrai.beakokit.model.StreamValidationResult
import org.akkirrai.beakokit.model.VideoStream
import org.akkirrai.beakokit.http.resolveUrl

class HttpStreamValidator(
    private val client: HttpClient,
) : StreamValidator {
    override suspend fun validate(stream: VideoStream): StreamValidationResult =
        try {
            when (stream.type) {
                StreamType.HLS -> validateHls(stream)
                StreamType.MP4 -> validateMp4(stream)
                StreamType.DASH -> validateDash(stream)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            failure(
                stream,
                null,
                listOfNotNull(error::class.simpleName, error.message).joinToString(": ").ifBlank {
                    "unknown error"
                },
            )
        }

    private suspend fun validateHls(stream: VideoStream): StreamValidationResult {
        val firstResponse = client.get(stream.url) {
            stream.headers.forEach { (name, value) -> header(name, value) }
        }
        if (!firstResponse.status.isSuccess()) {
            return failure(stream, firstResponse.status.value, "m3u8 вернул HTTP ${firstResponse.status.value}")
        }

        var playlistUrl = firstResponse.call.request.url.toString()
        var playlist = firstResponse.bodyAsText()
        if (!playlist.startsWith("#EXTM3U")) {
            return failure(stream, firstResponse.status.value, "Ответ не является HLS playlist")
        }

        val variant = selectBestVariant(playlist)
        if (variant != null) {
            playlistUrl = resolveUrl(playlistUrl, variant)
            val mediaResponse = client.get(playlistUrl) {
                stream.headers.forEach { (name, value) -> header(name, value) }
            }
            if (!mediaResponse.status.isSuccess()) {
                return failure(stream, mediaResponse.status.value, "media playlist вернул HTTP ${mediaResponse.status.value}")
            }
            playlist = mediaResponse.bodyAsText()
        }

        val hasSegments = playlist.lineSequence()
            .map(String::trim)
            .any { it.isNotEmpty() && !it.startsWith("#") }
        if (!hasSegments) {
            return failure(stream, 200, "В media playlist нет сегментов")
        }

        return StreamValidationResult(
            success = true,
            streamType = stream.type,
            quality = stream.quality,
            finalUrl = playlistUrl,
            statusCode = 200,
            message = "m3u8 загружен, media playlist содержит сегменты",
        )
    }

    private suspend fun validateMp4(stream: VideoStream): StreamValidationResult {
        val headResponse = client.head(stream.url) {
            stream.headers.forEach { (name, value) -> header(name, value) }
        }
        val rangeResponse = client.get(stream.url) {
            stream.headers.forEach { (name, value) -> header(name, value) }
            header(HttpHeaders.Range, "bytes=0-1023")
        }
        val bytes = rangeResponse.bodyAsBytes()
        if (!rangeResponse.status.isSuccess() || bytes.isEmpty()) {
            val headSummary = if (headResponse.status.isSuccess()) {
                "HEAD отработал успешно"
            } else {
                "HEAD вернул HTTP ${headResponse.status.value}"
            }
            return failure(stream, rangeResponse.status.value, "$headSummary, Range GET не вернул данные")
        }
        return StreamValidationResult(
            success = true,
            streamType = stream.type,
            quality = stream.quality,
            finalUrl = stream.url,
            statusCode = rangeResponse.status.value,
            message = if (headResponse.status.isSuccess()) {
                "HEAD и Range GET успешно вернули данные"
            } else {
                "HEAD вернул HTTP ${headResponse.status.value}, но Range GET успешно вернул данные"
            },
        )
    }

    private suspend fun validateDash(stream: VideoStream): StreamValidationResult {
        val manifestResponse = client.get(stream.url) {
            stream.headers.forEach { (name, value) -> header(name, value) }
        }
        if (!manifestResponse.status.isSuccess()) {
            return failure(stream, manifestResponse.status.value, "MPD вернул HTTP ${manifestResponse.status.value}")
        }
        val manifest = manifestResponse.bodyAsBytes()
        val manifestText = manifest.decodeToString()
        val selected = DASH_REPRESENTATION.findAll(manifestText)
            .mapNotNull { match ->
                val representationAttributes = match.groupValues[1]
                val representationBody = match.groupValues[2]
                val segmentTemplate = SEGMENT_TEMPLATE.find(representationBody)?.groupValues?.get(1)
                    ?: return@mapNotNull null
                val bandwidth = attrValue(representationAttributes, "bandwidth")?.toLongOrNull() ?: 0L
                val representationId = attrValue(representationAttributes, "id").orEmpty()
                val startNumber = attrValue(segmentTemplate, "startNumber")?.toIntOrNull() ?: 1
                DashSegment(
                    bandwidth = bandwidth,
                    initialization = substituteTemplate(
                        attrValue(segmentTemplate, "initialization").orEmpty(),
                        representationId,
                        startNumber,
                    ),
                    media = substituteTemplate(
                        attrValue(segmentTemplate, "media").orEmpty(),
                        representationId,
                        startNumber,
                    ),
                )
            }
            .maxByOrNull { it.bandwidth }
        val segment = selected
            ?: return failure(stream, 200, "MPD не содержит SegmentTemplate")

        for ((kind, reference) in listOf("init segment" to segment.initialization, "media segment" to segment.media)) {
            val response = client.get(resolveUrl(stream.url, reference)) {
                stream.headers.forEach { (name, value) -> header(name, value) }
                header(HttpHeaders.Range, "bytes=0-1023")
            }
            val bytes = response.bodyAsBytes()
            if (!response.status.isSuccess() || bytes.isEmpty()) {
                return failure(
                    stream,
                    response.status.value,
                    "$kind не отдаёт данные (HTTP ${response.status.value})",
                )
            }
        }

        return StreamValidationResult(
            success = true,
            streamType = stream.type,
            quality = stream.quality,
            finalUrl = stream.url,
            statusCode = 206,
            message = "MPD загружен, init и первый media segment отдают данные",
        )
    }

    private fun selectBestVariant(playlist: String): String? {
        val lines = playlist.lines()
        return lines.mapIndexedNotNull { index, line ->
            if (!line.startsWith("#EXT-X-STREAM-INF")) return@mapIndexedNotNull null
            val bandwidth = BANDWIDTH.find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val url = lines.drop(index + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }
            url?.let { bandwidth to it.trim() }
        }.maxByOrNull { it.first }?.second
    }

    private fun substituteTemplate(template: String, representationId: String, number: Int): String {
        val withRepresentation = template.replace("\$RepresentationID\$", representationId)
        return NUMBER_TEMPLATE.replace(withRepresentation) { match ->
            val width = match.groupValues[1].toIntOrNull()
            if (width == null) number.toString() else number.toString().padStart(width, '0')
        }
    }

    private fun failure(stream: VideoStream, statusCode: Int?, message: String) =
        StreamValidationResult(
            success = false,
            streamType = stream.type,
            quality = stream.quality,
            finalUrl = stream.url,
            statusCode = statusCode,
            message = message,
        )

    private companion object {
        val BANDWIDTH = Regex("""BANDWIDTH=(\d+)""")
        val NUMBER_TEMPLATE = Regex("""\${'$'}Number(?:%0(\d+)d)?\${'$'}""")
        val DASH_REPRESENTATION = Regex("""<Representation\b([^>]*)>(.*?)</Representation>""", RegexOption.DOT_MATCHES_ALL)
        val SEGMENT_TEMPLATE = Regex("""<SegmentTemplate\b([^>]*)/?>""", RegexOption.DOT_MATCHES_ALL)
        val ATTR = Regex("""\b([A-Za-z0-9:_-]+)\s*=\s*"([^"]*)"""")
    }

    private fun attrValue(block: String, name: String): String? =
        ATTR.findAll(block).firstOrNull { it.groupValues[1] == name }?.groupValues?.get(2)

    private data class DashSegment(
        val bandwidth: Long,
        val initialization: String,
        val media: String,
    )
}

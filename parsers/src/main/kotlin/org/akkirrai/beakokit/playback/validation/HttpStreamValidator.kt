package org.akkirrai.beakokit.playback.validation

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import org.akkirrai.beakokit.api.StreamValidator
import org.akkirrai.beakokit.model.StreamType
import org.akkirrai.beakokit.model.StreamValidationResult
import org.akkirrai.beakokit.model.VideoStream
import org.akkirrai.beakokit.http.resolveUrl
import java.util.concurrent.ConcurrentHashMap

class HttpStreamValidator(
    private val client: HttpClient,
    private val onTiming: ((stage: String, elapsedMs: Long, success: Boolean) -> Unit)? = null,
) : StreamValidator {
    private val successfulValidations = ConcurrentHashMap<ValidationKey, CachedValidation>()

    override suspend fun validate(stream: VideoStream): StreamValidationResult {
        // A loopback URL is a proxy the extension runs for itself: probing it only makes the proxy
        // fetch the upstream stream once more before the player fetches it again. A dead stream
        // still surfaces as a player error, which the watch flow retries with another candidate.
        if (stream.isLoopback()) {
            onTiming?.invoke("loopback_skipped", 0L, true)
            return StreamValidationResult(
                success = true,
                streamType = stream.type,
                quality = stream.quality,
                finalUrl = stream.url,
                statusCode = null,
                message = "Loopback stream is not probed",
            )
        }
        val cacheKey = stream.validationKey()
        successfulValidations[cacheKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.cachedAt < SUCCESS_CACHE_TTL_MS) {
                onTiming?.invoke("cache_hit", 0L, true)
                return cached.result
            }
            successfulValidations.remove(cacheKey, cached)
        }

        val validationStartedAt = System.nanoTime()
        val result = try {
            validateVideoAndAudio(stream)
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
        reportTiming("total", validationStartedAt, result.success)
        if (result.success) {
            successfulValidations[cacheKey] = CachedValidation(result, System.currentTimeMillis())
            trimValidationCache()
        }
        return result
    }

    /**
     * Video and a separately delivered audio rendition are independent network chains. Starting
     * them together removes one full playlist/segment round-trip on healthy streams, while both
     * still have to pass the same strict validation before the stream is accepted.
     *
     * If video fails first, cancel the auxiliary request instead of making a failing candidate
     * consume extra CDN capacity before PlaybackResolver tries its fallback.
     */
    private suspend fun validateVideoAndAudio(stream: VideoStream): StreamValidationResult = coroutineScope {
        val video = async {
            when (stream.type) {
                StreamType.HLS -> validateHls(stream, "video")
                StreamType.MP4 -> validateMp4(stream)
                StreamType.DASH -> validateDash(stream)
            }
        }
        val audio = stream.audioUrl?.takeIf(String::isNotBlank)?.let { audioUrl ->
            async {
                validateHls(
                    stream.copy(
                        url = audioUrl,
                        type = StreamType.HLS,
                        quality = null,
                        headers = stream.audioHeaders.ifEmpty { stream.headers },
                        audioUrl = null,
                        audioHeaders = emptyMap(),
                        subtitles = emptyList(),
                    ),
                    "audio",
                )
            }
        }
        val videoResult = video.await()
        if (!videoResult.success || audio == null) {
            audio?.cancelAndJoin()
            return@coroutineScope videoResult
        }
        val audioResult = audio.await()
        if (audioResult.success) {
            videoResult
        } else {
            failure(
                stream,
                audioResult.statusCode,
                "Аудиодорожка недоступна: ${audioResult.message}",
            )
        }
    }

    private fun VideoStream.validationKey() = ValidationKey(
        url = url,
        type = type,
        quality = quality,
        headers = headers,
        audioUrl = audioUrl,
        audioHeaders = audioHeaders,
    )

    private fun trimValidationCache() {
        val overflow = successfulValidations.size - MAX_SUCCESS_CACHE_ENTRIES
        if (overflow <= 0) return
        successfulValidations.entries
            .sortedBy { it.value.cachedAt }
            .take(overflow)
            .forEach { (key, value) -> successfulValidations.remove(key, value) }
    }

    private suspend fun validateHls(stream: VideoStream, track: String): StreamValidationResult {
        var requestStartedAt = System.nanoTime()
        val firstResponse = client.get(stream.url) {
            stream.headers.forEach { (name, value) -> header(name, value) }
        }
        if (!firstResponse.status.isSuccess()) {
            reportTiming("$track.hls_master", requestStartedAt, success = false)
            return failure(stream, firstResponse.status.value, "m3u8 вернул HTTP ${firstResponse.status.value}")
        }

        var playlistUrl = firstResponse.call.request.url.toString()
        var playlist = firstResponse.bodyAsText()
        reportTiming("$track.hls_master", requestStartedAt, success = true)
        if (!playlist.startsWith("#EXTM3U")) {
            return failure(stream, firstResponse.status.value, "Ответ не является HLS playlist")
        }

        val variant = selectBestVariant(playlist)
        if (variant != null) {
            playlistUrl = resolveUrl(playlistUrl, variant)
            requestStartedAt = System.nanoTime()
            val mediaResponse = client.get(playlistUrl) {
                stream.headers.forEach { (name, value) -> header(name, value) }
            }
            if (!mediaResponse.status.isSuccess()) {
                reportTiming("$track.hls_media_playlist", requestStartedAt, success = false)
                return failure(stream, mediaResponse.status.value, "media playlist вернул HTTP ${mediaResponse.status.value}")
            }
            playlist = mediaResponse.bodyAsText()
            reportTiming("$track.hls_media_playlist", requestStartedAt, success = true)
        }

        val firstSegment = playlist.lineSequence()
            .map(String::trim)
            .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
        if (firstSegment == null) {
            return failure(stream, 200, "В media playlist нет сегментов")
        }

        // A manifest may be public while its media objects require a cookie, Referer, or a URL
        // signature that has already expired. The player would otherwise discover the 403 only
        // after selecting this candidate, too late for PlaybackResolver to try its relay fallback.
        requestStartedAt = System.nanoTime()
        val segmentResponse = client.get(resolveUrl(playlistUrl, firstSegment)) {
            stream.headers.forEach { (name, value) -> header(name, value) }
            header(HttpHeaders.Range, "bytes=0-1023")
        }
        val segmentBytes = segmentResponse.bodyAsBytes()
        reportTiming(
            "$track.hls_first_segment",
            requestStartedAt,
            segmentResponse.status.isSuccess() && segmentBytes.isNotEmpty(),
        )
        if (!segmentResponse.status.isSuccess() || segmentBytes.isEmpty()) {
            return failure(
                stream,
                segmentResponse.status.value,
                "Первый HLS-сегмент не отдаёт данные (HTTP ${segmentResponse.status.value})",
            )
        }

        return StreamValidationResult(
            success = true,
            streamType = stream.type,
            quality = stream.quality,
            finalUrl = playlistUrl,
            statusCode = 200,
            message = "m3u8 и первый media segment отдают данные",
        )
    }

    private fun reportTiming(stage: String, startedAtNanos: Long, success: Boolean) {
        val elapsedMs = ((System.nanoTime() - startedAtNanos) / 1_000_000L).coerceAtLeast(0L)
        onTiming?.invoke(stage, elapsedMs, success)
    }

    private suspend fun validateMp4(stream: VideoStream): StreamValidationResult {
        val rangeResponse = client.get(stream.url) {
            stream.headers.forEach { (name, value) -> header(name, value) }
            header(HttpHeaders.Range, "bytes=0-1023")
        }
        val bytes = rangeResponse.bodyAsBytes()
        if (!rangeResponse.status.isSuccess() || bytes.isEmpty()) {
            return failure(stream, rangeResponse.status.value, "Range GET не вернул данные")
        }
        return StreamValidationResult(
            success = true,
            streamType = stream.type,
            quality = stream.quality,
            finalUrl = stream.url,
            statusCode = rangeResponse.status.value,
            message = "Range GET успешно вернул данные",
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

        val segmentResults = coroutineScope {
            listOf("init segment" to segment.initialization, "media segment" to segment.media)
                .map { (kind, reference) ->
                    async {
                        val response = client.get(resolveUrl(stream.url, reference)) {
                            stream.headers.forEach { (name, value) -> header(name, value) }
                            header(HttpHeaders.Range, "bytes=0-1023")
                        }
                        DashSegmentValidation(
                            kind = kind,
                            statusCode = response.status.value,
                            success = response.status.isSuccess() && response.bodyAsBytes().isNotEmpty(),
                        )
                    }
                }
                .awaitAll()
        }
        segmentResults.firstOrNull { !it.success }?.let { failed ->
            return failure(
                stream,
                failed.statusCode,
                "${failed.kind} не отдаёт данные (HTTP ${failed.statusCode})",
            )
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
        const val SUCCESS_CACHE_TTL_MS = 30_000L
        const val MAX_SUCCESS_CACHE_ENTRIES = 100
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

    private data class DashSegmentValidation(
        val kind: String,
        val statusCode: Int,
        val success: Boolean,
    )

    private data class ValidationKey(
        val url: String,
        val type: StreamType,
        val quality: String?,
        val headers: Map<String, String>,
        val audioUrl: String?,
        val audioHeaders: Map<String, String>,
    )

    private data class CachedValidation(
        val result: StreamValidationResult,
        val cachedAt: Long,
    )
}

private fun VideoStream.isLoopback(): Boolean {
    val host = url.substringAfter("://", "").substringBefore('/').substringBefore('?')
        .substringBeforeLast(':').trim('[', ']').lowercase()
    return audioUrl.isNullOrBlank() && (host == "127.0.0.1" || host == "localhost" || host == "::1")
}

package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType

class ProtocolBackedExternalSourceRuntimeTest {
    @Test
    fun searchSendsOperationAndDecodesPayload() = runBlocking {
        var received: ExternalSourceRuntimeRequest? = null
        var receivedLimits: ExternalSourceRuntimeCallLimits? = null
        val runtime = ProtocolBackedExternalSourceRuntime(
            transport = ExternalSourceRuntimeTransport { request, limits ->
                received = request
                receivedLimits = limits
                ExternalSourceRuntimeResponse(
                    requestId = request.requestId,
                    payload = buildJsonObject { put("count", 2) },
                )
            },
            payloadCodec = FakePayloadCodec(),
            requestIdFactory = { "request-1" },
        )

        val result = runtime.search(AnimeSearchRequest(query = "frieren"))

        assertEquals(ExternalSourceRuntimeOperation.SEARCH, received?.operation)
        assertEquals("frieren", received?.payload?.get("query")?.toString()?.trim('"'))
        assertEquals(SourceHostHttpRequest.DEFAULT_TIMEOUT_MILLIS, receivedLimits?.timeoutMillis)
        assertEquals(SourceHostHttpRequest.DEFAULT_MAX_RESPONSE_BYTES, receivedLimits?.maxResponseBytes)
        assertEquals(listOf("decoded-search"), result.map(AnimeTitle::id))
    }

    @Test
    fun playbackRuntimeSendsPlaybackOperationsAndDecodesPayloads() = runBlocking {
        val requests = mutableListOf<ExternalSourceRuntimeRequest>()
        val runtime = ProtocolBackedExternalSourcePlaybackRuntime(
            transport = ExternalSourceRuntimeTransport { request, _ ->
                requests += request
                ExternalSourceRuntimeResponse(
                    requestId = request.requestId,
                    payload = buildJsonObject { put("kind", request.operation.name) },
                )
            },
            payloadCodec = FakePlaybackPayloadCodec(),
            requestIdFactory = { "playback-${requests.size}" },
        )
        val title = wireTitle("title-1")
        val group = PlaybackGroup(
            id = "group-1",
            title = "Dub",
            episodes = listOf(Episode("episode-1", 1.0, "Episode 1")),
        )

        assertEquals(listOf(group), runtime.playbackGroups(title))
        assertEquals(
            listOf(PlayerLink("https://example.test/video.mp4", PlayerType.DIRECT_MP4, "720p")),
            runtime.playerLinks(title, group, group.episodes.single()),
        )
        assertEquals(ExternalSourceRuntimeOperation.PLAYBACK_GROUPS, requests[0].operation)
        assertEquals(ExternalSourceRuntimeOperation.PLAYER_LINKS, requests[1].operation)
        assertEquals("group-1", requests[1].payload["groupId"]?.toString()?.trim('"'))
    }

    @Test
    fun latestRuntimeSendsLatestOperationAndLimit() = runBlocking {
        var received: ExternalSourceRuntimeRequest? = null
        val runtime = ProtocolBackedExternalSourceLatestRuntime(
            transport = ExternalSourceRuntimeTransport { request, _ ->
                received = request
                ExternalSourceRuntimeResponse(
                    requestId = request.requestId,
                    payload = buildJsonObject { put("items", "ignored") },
                )
            },
            payloadCodec = FakePayloadCodec(),
            requestIdFactory = { "latest-1" },
        )

        assertEquals(listOf("decoded-search"), runtime.latest(12).map(AnimeTitle::id))
        assertEquals(ExternalSourceRuntimeOperation.LATEST, received?.operation)
        assertEquals("12", received?.payload?.get("limit")?.toString())
    }

    @Test
    fun latestRuntimeRejectsNonPositiveLimitBeforeTransport() = runBlocking {
        var transportCalled = false
        val runtime = ProtocolBackedExternalSourceLatestRuntime(
            transport = ExternalSourceRuntimeTransport { _, _ ->
                transportCalled = true
                error("Transport must not be called")
            },
            payloadCodec = FakePayloadCodec(),
            requestIdFactory = { "latest-invalid" },
        )

        assertFailsWith<IllegalArgumentException> { runtime.latest(0) }
        assertEquals(false, transportCalled)
    }

    @Test
    fun mismatchedResponseIdIsRejected() = runBlocking {
        val runtime = ProtocolBackedExternalSourceRuntime(
            transport = ExternalSourceRuntimeTransport { request, _ ->
                ExternalSourceRuntimeResponse(
                    requestId = "different-request",
                    payload = buildJsonObject { put("count", 0) },
                )
            },
            payloadCodec = FakePayloadCodec(),
            requestIdFactory = { "request-1" },
        )

        val exception = assertFailsWith<SourceException> {
            runtime.details("title-1")
        }

        assertEquals(SourceErrorKind.PARSE, exception.kind)
    }

    @Test
    fun protocolAdapterDecodesTheCanonicalAnimeTitlePayload() = runBlocking {
        val expected = wireTitle("decoded-from-wire")
        val runtime = ProtocolBackedExternalSourceRuntime(
            transport = ExternalSourceRuntimeTransport { request, _ ->
                ExternalSourceRuntimeResponse(
                    requestId = request.requestId,
                    payload = AnimeTitleRuntimePayloadCodec.encodeDetails(expected),
                )
            },
            payloadCodec = AnimeTitleRuntimePayloadCodec,
            requestIdFactory = { "request-2" },
        )

        assertEquals(expected, runtime.details("title-1"))
    }

    @Test
    fun nativeByteBridgeFeedsTheProtocolBackedRuntime() = runBlocking {
        val expected = wireTitle("decoded-from-native-bridge")
        var receivedRequest: ExternalSourceRuntimeRequest? = null
        val transport = NativeBridgeExternalSourceRuntimeTransport(
            ExternalSourceRuntimeNativeBridge { request, _ ->
                receivedRequest = ExternalSourceRuntimeProtocolCodec.decodeRequest(request)
                ExternalSourceRuntimeProtocolCodec.encodeResponse(
                    ExternalSourceRuntimeResponse(
                        requestId = receivedRequest!!.requestId,
                        payload = AnimeTitleRuntimePayloadCodec.encodeDetails(expected),
                    ),
                )
            },
        )
        val runtime = ProtocolBackedExternalSourceRuntime(
            transport = transport,
            payloadCodec = AnimeTitleRuntimePayloadCodec,
            requestIdFactory = { "native-bridge-1" },
        )

        val result = runtime.details("title-1")

        assertEquals(expected, result)
        assertEquals(ExternalSourceRuntimeOperation.DETAILS, receivedRequest?.operation)
    }

    @Test
    fun invalidDecodedPayloadBecomesParseSourceException() = runBlocking {
        val runtime = ProtocolBackedExternalSourceRuntime(
            transport = ExternalSourceRuntimeTransport { request, _ ->
                ExternalSourceRuntimeResponse(
                    requestId = request.requestId,
                    payload = buildJsonObject { put("items", "not-an-array") },
                )
            },
            payloadCodec = AnimeTitleRuntimePayloadCodec,
            requestIdFactory = { "request-3" },
        )

        val exception = assertFailsWith<SourceException> {
            runtime.search(AnimeSearchRequest(query = "broken"))
        }

        assertEquals(SourceErrorKind.PARSE, exception.kind)
    }

    @Test
    fun cancellationIsNotConvertedToParseFailure() = runBlocking {
        val cancellation = CancellationException("cancelled by caller")
        val runtime = ProtocolBackedExternalSourceRuntime(
            transport = ExternalSourceRuntimeTransport { _, _ -> throw cancellation },
            payloadCodec = AnimeTitleRuntimePayloadCodec,
            requestIdFactory = { "request-4" },
        )

        val thrown = assertFailsWith<CancellationException> {
            runtime.details("title-1")
        }

        assertEquals(cancellation.message, thrown.message)
    }

    @Test
    fun internalTransportErrorsAreNotConvertedToSourceFailures() = runBlocking {
        val runtime = ProtocolBackedExternalSourceRuntime(
            transport = ExternalSourceRuntimeTransport { _, _ ->
                throw AssertionError("internal runtime failure")
            },
            payloadCodec = AnimeTitleRuntimePayloadCodec,
            requestIdFactory = { "request-internal-error" },
        )

        val thrown = assertFailsWith<AssertionError> {
            runtime.details("title-1")
        }

        assertEquals("internal runtime failure", thrown.message)
    }

    @Test
    fun cancelledProtocolResponseCancelsTheCaller() = runBlocking {
        val runtime = ProtocolBackedExternalSourceRuntime(
            transport = ExternalSourceRuntimeTransport { request, _ ->
                ExternalSourceRuntimeResponse(
                    requestId = request.requestId,
                    errorCode = ExternalSourceRuntimeErrorCode.CANCELLED,
                )
            },
            payloadCodec = AnimeTitleRuntimePayloadCodec,
            requestIdFactory = { "request-5" },
        )

        assertFailsWith<CancellationException> {
            runtime.details("title-1")
        }
    }

    @Test
    fun transportFailureBecomesRuntimeSourceException() = runBlocking {
        val runtime = ProtocolBackedExternalSourceRuntime(
            transport = ExternalSourceRuntimeTransport { _, _ -> error("native runtime failed") },
            payloadCodec = AnimeTitleRuntimePayloadCodec,
            requestIdFactory = { "request-6" },
        )

        val exception = assertFailsWith<SourceException> {
            runtime.details("title-1")
        }

        assertEquals(SourceErrorKind.UNAVAILABLE, exception.kind)
        assertEquals(SourceErrorCode.RUNTIME_FAILURE, exception.code)
    }

    @Test
    fun runtimeCallTimesOutAtTheCommonBoundary() = runBlocking {
        val runtime = ProtocolBackedExternalSourceRuntime(
            transport = ExternalSourceRuntimeTransport { _, _ ->
                delay(100)
                error("Transport should have timed out")
            },
            payloadCodec = AnimeTitleRuntimePayloadCodec,
            requestIdFactory = { "request-7" },
            callLimits = ExternalSourceRuntimeCallLimits(timeoutMillis = 10, maxResponseBytes = 1024),
        )

        assertFailsWith<SourceUnavailableException> {
            runtime.details("title-1")
        }
    }

    @Test
    fun oversizedRuntimePayloadIsRejectedBeforeDecoding() = runBlocking {
        val runtime = ProtocolBackedExternalSourceRuntime(
            transport = ExternalSourceRuntimeTransport { request, _ ->
                ExternalSourceRuntimeResponse(
                    requestId = request.requestId,
                    payload = buildJsonObject { put("payload", "x".repeat(128)) },
                )
            },
            payloadCodec = FakePayloadCodec(),
            requestIdFactory = { "request-8" },
            callLimits = ExternalSourceRuntimeCallLimits(timeoutMillis = 1_000, maxResponseBytes = 32),
        )

        assertFailsWith<SourceUnavailableException> {
            runtime.details("title-1")
        }
    }

    @Test
    fun oversizedRuntimeRequestIsRejectedBeforeTransport() = runBlocking {
        var transportCalled = false
        val runtime = ProtocolBackedExternalSourceRuntime(
            transport = ExternalSourceRuntimeTransport { _, _ ->
                transportCalled = true
                error("Transport must not be called")
            },
            payloadCodec = FakePayloadCodec(),
            requestIdFactory = { "request-9" },
            callLimits = ExternalSourceRuntimeCallLimits(timeoutMillis = 1_000, maxRequestBytes = 32),
        )

        val exception = assertFailsWith<SourceException> {
            runtime.search(AnimeSearchRequest(query = "x".repeat(128)))
        }

        assertEquals(SourceErrorKind.PARSE, exception.kind)
        assertEquals(SourceErrorCode.INVALID_REQUEST, exception.code)
        assertEquals(false, transportCalled)
    }

    @Test
    fun oversizedRuntimeErrorEnvelopeIsRejectedBeforeErrorMapping() = runBlocking {
        val runtime = ProtocolBackedExternalSourceRuntime(
            transport = ExternalSourceRuntimeTransport { request, _ ->
                ExternalSourceRuntimeResponse(
                    requestId = request.requestId,
                    errorCode = ExternalSourceRuntimeErrorCode.SOURCE_FAILURE,
                    errorMessage = "x".repeat(128),
                )
            },
            payloadCodec = FakePayloadCodec(),
            requestIdFactory = { "request-10" },
            callLimits = ExternalSourceRuntimeCallLimits(timeoutMillis = 1_000, maxResponseBytes = 32),
        )

        assertFailsWith<SourceUnavailableException> {
            runtime.details("title-1")
        }
    }

    private fun wireTitle(id: String) = AnimeTitle(
        id = id,
        russianName = null,
        englishName = id,
        originalName = id,
        japaneseName = null,
        synonyms = emptyList(),
        year = null,
        type = null,
        episodeCount = null,
        posterUrl = null,
        status = null,
        description = null,
    )

    private class FakePayloadCodec : ExternalSourceRuntimePayloadCodec {
        override fun decodeSearch(payload: JsonObject): List<AnimeTitle> = listOf(title("decoded-search"))

        override fun decodeDetails(payload: JsonObject): AnimeTitle = title("decoded-details")

        private fun title(id: String) = AnimeTitle(
            id = id,
            russianName = null,
            englishName = id,
            originalName = id,
            japaneseName = null,
            synonyms = emptyList(),
            year = null,
            type = null,
            episodeCount = null,
            posterUrl = null,
            status = null,
            description = null,
        )
    }

    private class FakePlaybackPayloadCodec : ExternalSourcePlaybackRuntimePayloadCodec {
        override fun decodeSearch(payload: JsonObject): List<AnimeTitle> = emptyList()

        override fun decodeDetails(payload: JsonObject): AnimeTitle = title("decoded-details")

        override fun decodePlaybackGroups(payload: JsonObject): List<PlaybackGroup> = listOf(
            PlaybackGroup(
                id = "group-1",
                title = "Dub",
                episodes = listOf(Episode("episode-1", 1.0, "Episode 1")),
            ),
        )

        override fun decodePlayerLinks(payload: JsonObject): List<PlayerLink> = listOf(
            PlayerLink("https://example.test/video.mp4", PlayerType.DIRECT_MP4, "720p"),
        )

        private fun title(id: String) = AnimeTitle(
            id = id,
            russianName = null,
            englishName = id,
            originalName = id,
            japaneseName = null,
            synonyms = emptyList(),
            year = null,
            type = null,
            episodeCount = null,
            posterUrl = null,
            status = null,
            description = null,
        )
    }
}

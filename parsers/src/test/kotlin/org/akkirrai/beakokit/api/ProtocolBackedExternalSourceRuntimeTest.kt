package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle

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

        assertEquals(cancellation, thrown)
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
}

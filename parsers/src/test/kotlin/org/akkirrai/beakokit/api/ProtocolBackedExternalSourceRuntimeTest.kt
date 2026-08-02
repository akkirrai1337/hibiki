package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        val runtime = ProtocolBackedExternalSourceRuntime(
            transport = ExternalSourceRuntimeTransport { request ->
                received = request
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
        assertEquals(listOf("decoded-search"), result.map(AnimeTitle::id))
    }

    @Test
    fun mismatchedResponseIdIsRejected() = runBlocking {
        val runtime = ProtocolBackedExternalSourceRuntime(
            transport = ExternalSourceRuntimeTransport {
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

    private class FakePayloadCodec : ExternalSourceRuntimePayloadCodec {
        override fun encodeSearch(request: AnimeSearchRequest): JsonObject = buildJsonObject {
            put("query", request.query)
        }

        override fun decodeSearch(payload: JsonObject): List<AnimeTitle> = listOf(title("decoded-search"))

        override fun encodeDetails(id: String): JsonObject = buildJsonObject { put("id", id) }

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

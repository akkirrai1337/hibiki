package org.akkirrai.hibiki

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.akkirrai.beakokit.runtime.NativeSourceRuntimeBridge
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class WasmtimeRuntimeInstrumentedTest {
    @Test
    fun productionBridgeExecutesRealRustAniLibertyPackage() {
        val search = NativeSourceRuntimeBridge.protocolModuleCallWithHost(
            module = anilibertyModuleBytes(),
            request = """
                {"requestId":"ani-search-1","operation":"SEARCH","payload":{"query":"naruto","limit":20,"offset":0,"sort":"RELEVANCE","typeAliases":[],"statusAliases":[],"includedGenreAliases":[],"excludedGenreAliases":[],"yearFrom":null,"yearTo":null},"protocolVersion":1}
            """.trimIndent(),
            host = anilibertyHost(),
            cancellationScopeId = 0L,
        )
        assertTrue("Unexpected AniLiberty search response: $search", search.contains("\"id\":\"413\""))
        assertTrue("AniLiberty search title was not mapped: $search", search.contains("\"originalName\":\"Naruto\""))

        val details = NativeSourceRuntimeBridge.protocolModuleCallWithHost(
            module = anilibertyModuleBytes(),
            request = """
                {"requestId":"ani-details-1","operation":"DETAILS","payload":{"id":"413"},"protocolVersion":1}
            """.trimIndent(),
            host = anilibertyHost(),
            cancellationScopeId = 0L,
        )
        assertTrue("Unexpected AniLiberty details response: $details", details.contains("\"id\":\"413\""))
        assertTrue("Unexpected AniLiberty title response: $details", details.contains("\"originalName\":\"Naruto\""))

        val groups = NativeSourceRuntimeBridge.protocolModuleCallWithHost(
            module = anilibertyModuleBytes(),
            request = """
                {"requestId":"ani-groups-1","operation":"PLAYBACK_GROUPS","payload":{"titleId":"413"},"protocolVersion":1}
            """.trimIndent(),
            host = anilibertyHost(),
            cancellationScopeId = 0L,
        )
        assertTrue("Unexpected AniLiberty playback groups response: $groups", groups.contains("\"id\":\"episode-1\""))
        assertTrue("AniLiberty episode number was not mapped: $groups", groups.contains("\"number\":1.0"))

        val links = NativeSourceRuntimeBridge.protocolModuleCallWithHost(
            module = anilibertyModuleBytes(),
            request = """
                {"requestId":"ani-links-1","operation":"PLAYER_LINKS","payload":{"titleId":"413","groupId":"413","episodeId":"episode-1","episodeNumber":1.0},"protocolVersion":1}
            """.trimIndent(),
            host = anilibertyHost(),
            cancellationScopeId = 0L,
        )
        assertTrue("Unexpected AniLiberty player links response: $links", links.contains("720.m3u8"))
        assertTrue("Opening segment was not mapped: $links", links.contains("OPENING"))
    }

    @Test
    fun productionBridgeExecutesModuleAndReturnsHostResponse() {
        val request = """
            {"requestId":"instrumented-1","operation":"DETAILS","payload":{"id":"title-1"},"protocolVersion":1}
        """.trimIndent()
        val response = NativeSourceRuntimeBridge.protocolModuleCallWithHost(
            module = moduleBytes(),
            request = request,
            host = NativeSourceRuntimeBridge.Host {
                """
                    {"requestId":"instrumented-1","payload":{"id":"title-1","originalName":"Fixture","synonyms":[],"genres":[],"screenshots":[],"studios":[],"ratings":[],"mainCharacters":[],"similarAnime":[],"franchiseAnime":[],"relatedAnime":[]},"errorCode":null,"errorMessage":null,"protocolVersion":1}
                """.trimIndent().toByteArray(StandardCharsets.UTF_8)
            },
            cancellationScopeId = 0L,
        )

        assertTrue(response.contains("\"requestId\":\"instrumented-1\""))
        assertTrue(response.contains("\"id\":\"title-1\""))
        assertTrue(response.contains("\"originalName\":\"Fixture\""))
    }

    @Test
    fun productionBridgeRejectsInvalidModule() {
        assertThrows(Throwable::class.java) {
            NativeSourceRuntimeBridge.validateModule(byteArrayOf(0, 1, 2, 3))
        }
    }

    @Test
    fun productionBridgeForwardsPlaybackOperations() {
        val operations = listOf(
            "PLAYBACK_GROUPS" to "groups",
            "PLAYER_LINKS" to "links",
        )

        operations.forEachIndexed { index, (operation, payloadKey) ->
            val requestId = "playback-$index"
            val payload = if (operation == "PLAYBACK_GROUPS") {
                "{\"titleId\":\"title-1\"}"
            } else {
                "{\"titleId\":\"title-1\",\"groupId\":\"group-1\",\"episodeId\":\"episode-1\",\"episodeNumber\":1.0}"
            }
            val response = NativeSourceRuntimeBridge.protocolModuleCallWithHost(
                module = moduleBytes(),
                request = """
                    {"requestId":"$requestId","operation":"$operation","payload":$payload,"protocolVersion":1}
                """.trimIndent(),
                host = NativeSourceRuntimeBridge.Host {
                    """
                        {"requestId":"$requestId","payload":{"$payloadKey":[]},"errorCode":null,"errorMessage":null,"protocolVersion":1}
                    """.trimIndent().toByteArray(StandardCharsets.UTF_8)
                },
                cancellationScopeId = 0L,
            )

            assertTrue("Unexpected native response: $response", response.contains("\"requestId\":\"$requestId\""))
            assertTrue("Unexpected native response: $response", response.contains("\"$payloadKey\":[]"))
        }
    }

    @Test
    fun productionBridgeRejectsMalformedPlaybackResponse() {
        val response = NativeSourceRuntimeBridge.protocolModuleCallWithHost(
            module = moduleBytes(),
            request = """
                {"requestId":"invalid-playback-1","operation":"PLAYBACK_GROUPS","payload":{"titleId":"title-1"},"protocolVersion":1}
            """.trimIndent(),
            host = NativeSourceRuntimeBridge.Host {
                """
                    {"requestId":"invalid-playback-1","payload":{"groups":[{"id":"group-1"}]},"errorCode":null,"errorMessage":null,"protocolVersion":1}
                """.trimIndent().toByteArray(StandardCharsets.UTF_8)
            },
            cancellationScopeId = 0L,
        )

        assertTrue("Unexpected malformed response result: $response", response.contains("RUNTIME_FAILURE"))
    }

    @Test
    fun productionBridgeCancellationStopsGuestLoop() {
        val scopeId = NativeSourceRuntimeBridge.beginCancellationScope()
        val response = AtomicReference<String?>()
        val worker = Thread {
            response.set(
                NativeSourceRuntimeBridge.protocolModuleCallWithHost(
                    module = loopingModuleBytes(),
                    request = """
                        {"requestId":"cancel-1","operation":"DETAILS","payload":{"id":"title-1"},"protocolVersion":1}
                    """.trimIndent(),
                    host = NativeSourceRuntimeBridge.Host { error("Host must not be called") },
                    cancellationScopeId = scopeId,
                ),
            )
        }

        try {
            worker.start()
            Thread.sleep(100)
            NativeSourceRuntimeBridge.cancelCancellationScope(scopeId)
            worker.join(5_000)

            assertTrue("Guest loop did not stop after cancellation", !worker.isAlive)
            assertTrue(
                "Unexpected cancellation response: ${response.get()}",
                response.get()?.contains("RUNTIME_FAILURE") == true,
            )
        } finally {
            if (worker.isAlive) worker.interrupt()
            NativeSourceRuntimeBridge.finishCancellationScope(scopeId)
        }
    }

    private fun moduleBytes(): ByteArray = """
        (module
          (import "host" "call" (func ${'$'}host_call (param i32 i32) (result i64)))
          (memory (export "memory") 0)
          (global ${'$'}heap (mut i32) (i32.const 4096))
          (func (export "beakokit_reset")
            i32.const 4096
            global.set ${'$'}heap)
          (func (export "beakokit_alloc") (param i32) (result i32)
            global.get ${'$'}heap
            global.get ${'$'}heap
            local.get 0
            i32.add
            global.set ${'$'}heap)
          (func (export "beakokit_call") (param i32 i32) (result i64)
            local.get 0
            local.get 1
            call ${'$'}host_call))
    """.trimIndent().toByteArray(StandardCharsets.UTF_8)

    private fun anilibertyModuleBytes(): ByteArray =
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("aniliberty-source.wasm")
            .use { it.readBytes() }

    private fun anilibertyHost(): NativeSourceRuntimeBridge.Host = NativeSourceRuntimeBridge.Host { request ->
        val envelope = JSONObject(request.decodeToString())
        val url = envelope.getJSONObject("payload").getString("url")
        val body = if (url.contains("anime/releases/")) {
            """{"data":${aniLibertyReleaseJson()}}"""
        } else {
            """{"data":[${aniLibertyReleaseJson()}]}"""
        }
        JSONObject()
            .put("requestId", envelope.getString("requestId"))
            .put(
                "payload",
                JSONObject()
                    .put("statusCode", 200)
                    .put("headers", JSONObject())
                    .put("body", body),
            )
            .put("errorCode", JSONObject.NULL)
            .put("errorMessage", JSONObject.NULL)
            .put("protocolVersion", 1)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
    }

    private fun aniLibertyReleaseJson(): String = """
        {
          "id":413,
          "name":{"main":"Naruto","english":"Naruto","alternative":null},
          "year":2007,
          "type":{"value":"TV"},
          "episodes_total":1,
          "is_ongoing":false,
          "description":"Fixture release",
          "poster":{"src":"/storage/poster.jpg"},
          "genres":[{"name":"Action"}],
          "episodes":[{
            "id":"episode-1",
            "ordinal":1,
            "name":"Episode 1",
            "hls_720":"https://cache.libria.fun/videos/episode-1/720.m3u8",
            "duration":1400,
            "opening":{"start":1,"stop":100},
            "ending":{"start":null,"stop":null}
          }]
        }
    """.trimIndent()

    private fun loopingModuleBytes(): ByteArray = """
        (module
          (memory (export "memory") 2)
          (func (export "beakokit_reset"))
          (func (export "beakokit_alloc") (param i32) (result i32)
            i32.const 4096)
          (func (export "beakokit_call") (param i32 i32) (result i64)
            (loop br 0))
        )
    """.trimIndent().toByteArray(StandardCharsets.UTF_8)
}

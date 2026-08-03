package org.akkirrai.hibiki

import androidx.test.ext.junit.runners.AndroidJUnit4
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
          (memory (export "memory") 2)
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

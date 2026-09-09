package org.akkirrai.beakokit.extension

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.context.DefaultSourceContext
import org.akkirrai.beakokit.api.context.ExtensionStorage
import org.akkirrai.beakokit.api.context.MapExtensionStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The `storage` global is what makes a signed-in source possible at all: a token has to outlive
 * the call that fetched it. These pin the parts a script actually depends on - that a value read
 * back is the value written, that a key never set reads as null rather than as undefined, and that
 * a store survives from one call to the next - plus the case a host with nowhere to persist to has
 * to survive: writes that go nowhere must not throw.
 *
 * The same guarantees are checked on the desktop side by `extensionCallStorage.test.ts`; the two
 * hosts reach them differently (a snapshot and collected writes there, a direct write here), which
 * is exactly why both are tested rather than one being assumed from the other.
 */
class RhinoExtensionRuntimeStorageTest {
    private fun runtimeWith(storage: ExtensionStorage, payload: String) = RhinoExtensionRuntime(
        extensionId = "storage-test",
        payload = payload,
        sourceContext = DefaultSourceContext(
            httpClient = HttpClient(MockEngine { respond(content = "", status = HttpStatusCode.OK) }),
            preferredLanguages = listOf(SourceLanguage.ENGLISH),
            extensionStorage = storage,
        ),
    )

    @Test
    fun `a value written by one call is readable by the next`() {
        val storage = MapExtensionStorage()
        val runtime = runtimeWith(
            storage,
            """
            var Provider = {
                signIn: function () { storage.set("token", "abc"); return true; },
                token: function () { return storage.get("token"); }
            };
            """.trimIndent(),
        )

        runtime.callRaw("signIn", emptyArray())

        assertEquals("\"abc\"", runtime.callRaw("token", emptyArray()))
        assertEquals(mapOf("token" to "abc"), storage.snapshot())
    }

    @Test
    fun `a key that was never set reads as null`() {
        val runtime = runtimeWith(
            MapExtensionStorage(),
            """
            var Provider = {
                missing: function () { return storage.get("nope") === null; }
            };
            """.trimIndent(),
        )

        assertEquals("true", runtime.callRaw("missing", emptyArray()))
    }

    @Test
    fun `a script sees what the host put there before it ran`() {
        // The signed-in case on every launch after the first: the token is already in the store
        // when the script starts, and it must be able to just read it.
        val runtime = runtimeWith(
            MapExtensionStorage(mapOf("token" to "restored")),
            """
            var Provider = { token: function () { return storage.get("token"); } };
            """.trimIndent(),
        )

        assertEquals("\"restored\"", runtime.callRaw("token", emptyArray()))
    }

    @Test
    fun `removing a key is not the same as writing an empty one`() {
        // Signing out has to actually delete, or the next launch reads back a token that is no
        // longer valid and the source looks signed in when it is not.
        val storage = MapExtensionStorage(mapOf("token" to "abc"))
        val runtime = runtimeWith(
            storage,
            """
            var Provider = { signOut: function () { storage.remove("token"); return true; } };
            """.trimIndent(),
        )

        runtime.callRaw("signOut", emptyArray())

        assertNull(storage.get("token"))
        assertEquals(emptyMap(), storage.snapshot())
    }

    @Test
    fun `a host with nowhere to persist forgets writes without failing`() {
        // ExtensionStorage.NONE is what a host uses when it has no store - tooling, tests, or a
        // platform where persistence isn't wired up yet. A script must degrade to "not signed in",
        // never to an error.
        val runtime = runtimeWith(
            ExtensionStorage.NONE,
            """
            var Provider = {
                roundTrip: function () {
                    storage.set("token", "abc");
                    return storage.get("token");
                }
            };
            """.trimIndent(),
        )

        assertEquals("null", runtime.callRaw("roundTrip", emptyArray()))
    }
}

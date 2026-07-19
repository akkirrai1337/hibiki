package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking

class ChallengeSessionTest {
    @Test
    fun `request only accepts https urls and explicit completion cookies`() {
        assertFailsWith<IllegalArgumentException> {
            ChallengeSessionRequest("http://source.test", setOf("cf_clearance"))
        }
        assertFailsWith<IllegalArgumentException> {
            ChallengeSessionRequest("https://source.test", emptySet())
        }
        assertFailsWith<IllegalArgumentException> {
            ChallengeSessionRequest("https://source.test", setOf("bad cookie"))
        }
    }

    @Test
    fun `session exposes matching cookie and user agent headers`() {
        val session = ChallengeSession(
            cookies = linkedMapOf("cf_clearance" to "clearance", "session" to "value=part"),
            userAgent = "Browser agent",
        )

        assertEquals("cf_clearance=clearance; session=value=part", session.cookieHeader)
        assertEquals(
            mapOf(
                "Cookie" to "cf_clearance=clearance; session=value=part",
                "User-Agent" to "Browser agent",
            ),
            session.headers(),
        )
    }

    @Test
    fun `unsupported provider reports host capability clearly`() {
        assertFailsWith<SourceUnavailableException> {
            runBlocking {
                ChallengeSessionProvider.UNSUPPORTED.acquire(
                    ChallengeSessionRequest("https://source.test", setOf("cf_clearance")),
                )
            }
        }
    }
}

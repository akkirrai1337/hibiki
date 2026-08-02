package org.akkirrai.beakokit.api

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class SourceHostChallengeTest {
    @Test
    fun `challenge provider rejects undeclared capability`() = runBlocking {
        val provider = SourceHostChallengeProvider(
            requirements = SourceHostRequirements(),
            delegate = successfulProvider(),
        )

        assertFailsWith<SourceHostCapabilityException> {
            provider.acquire(request())
        }
    }

    @Test
    fun `challenge provider delegates declared capability`() = runBlocking {
        var called = false
        val provider = SourceHostChallengeProvider(
            requirements = SourceHostRequirements(setOf(SourceHostCapability.CHALLENGE)),
            delegate = ChallengeSessionProvider {
                called = true
                ChallengeSession(cookies = mapOf("clearance" to "value"), userAgent = "test")
            },
        )

        assertNotNull(provider.acquire(request()))
        check(called)
    }

    private fun successfulProvider() = ChallengeSessionProvider {
        ChallengeSession(cookies = mapOf("clearance" to "value"), userAgent = "test")
    }

    private fun request() = ChallengeSessionRequest(
        url = "https://example.com/challenge",
        requiredCookieNames = setOf("clearance"),
    )
}

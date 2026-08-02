package org.akkirrai.beakokit.api

/** Adds manifest capability enforcement to the existing challenge/WebView provider. */
class SourceHostChallengeProvider(
    private val requirements: SourceHostRequirements,
    private val delegate: ChallengeSessionProvider,
) : ChallengeSessionProvider {
    override suspend fun acquire(request: ChallengeSessionRequest): ChallengeSession {
        if (!requirements.requires(SourceHostCapability.CHALLENGE)) {
            throw SourceHostCapabilityException(SourceHostCapability.CHALLENGE)
        }
        return delegate.acquire(request)
    }
}


package org.akkirrai.beakokit.api

import io.ktor.client.HttpClient

/** Host-owned services exposed to a source without leaking Android dependencies into it. */
interface SourceContext {
    val httpClient: HttpClient
    val preferredLanguages: List<SourceLanguage>
    val config: SourceConfig
    val logger: SourceLogger
    val sourceHealthReporter: SourceHealthReporter
        get() = SourceHealthReporter.NONE
    val challengeSessionProvider: ChallengeSessionProvider
        get() = ChallengeSessionProvider.UNSUPPORTED
    val sourceExecutionPolicy: SourceExecutionPolicy
        get() = SourceExecutionPolicy.NONE
}

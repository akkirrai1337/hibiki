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

/** Small default implementation useful to both the common host and source tests. */
data class DefaultSourceContext(
    override val httpClient: HttpClient,
    override val preferredLanguages: List<SourceLanguage>,
    override val config: SourceConfig = SourceConfig.EMPTY,
    override val logger: SourceLogger = SourceLogger.NONE,
    override val sourceHealthReporter: SourceHealthReporter = SourceHealthReporter.NONE,
    override val challengeSessionProvider: ChallengeSessionProvider = ChallengeSessionProvider.UNSUPPORTED,
    override val sourceExecutionPolicy: SourceExecutionPolicy =
        HealthTrackingSourceExecutionPolicy(sourceHealthReporter),
) : SourceContext {
    init {
        require(preferredLanguages.isNotEmpty()) { "At least one preferred language is required" }
    }
}

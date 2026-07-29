package org.akkirrai.beakokit.api

import io.ktor.client.HttpClient

/** Small default implementation useful to both the host application and source tests. */
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

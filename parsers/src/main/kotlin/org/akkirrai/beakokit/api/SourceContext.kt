package org.akkirrai.beakokit.api

import io.ktor.client.HttpClient

/** Host-owned services exposed to a source without leaking Android dependencies into it. */
interface SourceContext {
    val httpClient: HttpClient
    val preferredLanguages: List<SourceLanguage>
    val logger: SourceLogger
}

enum class SourceLogLevel {
    DEBUG,
    WARNING,
    ERROR,
}

fun interface SourceLogger {
    fun log(level: SourceLogLevel, message: String, throwable: Throwable?)

    companion object {
        val NONE = SourceLogger { _, _, _ -> }
    }
}

/** Small default implementation useful to both the host application and source tests. */
data class DefaultSourceContext(
    override val httpClient: HttpClient,
    override val preferredLanguages: List<SourceLanguage>,
    override val logger: SourceLogger = SourceLogger.NONE,
) : SourceContext {
    init {
        require(preferredLanguages.isNotEmpty()) { "At least one preferred language is required" }
    }
}

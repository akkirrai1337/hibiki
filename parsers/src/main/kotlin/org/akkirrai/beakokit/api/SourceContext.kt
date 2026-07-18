package org.akkirrai.beakokit.api

import io.ktor.client.HttpClient

/** Host-owned services exposed to a source without leaking Android dependencies into it. */
interface SourceContext {
    val httpClient: HttpClient
    val preferredLanguages: List<SourceLanguage>
    val config: SourceConfig
    val logger: SourceLogger
}

/** Source-scoped values supplied by the host; secrets are deliberately read separately. */
interface SourceConfig {
    fun value(key: String): String?
    fun secret(key: String): String?

    companion object {
        val EMPTY = MapSourceConfig()
    }
}

class MapSourceConfig(
    values: Map<String, String> = emptyMap(),
    secrets: Map<String, String> = emptyMap(),
) : SourceConfig {
    private val values = values.toMap()
    private val secrets = secrets.toMap()

    override fun value(key: String): String? = values[key]

    override fun secret(key: String): String? = secrets[key]
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
    override val config: SourceConfig = SourceConfig.EMPTY,
    override val logger: SourceLogger = SourceLogger.NONE,
) : SourceContext {
    init {
        require(preferredLanguages.isNotEmpty()) { "At least one preferred language is required" }
    }
}

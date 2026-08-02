package org.akkirrai.beakokit.api

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

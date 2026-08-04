package org.akkirrai.beakokit.api

/** Source-scoped values supplied by the host; secrets are deliberately read separately. */
interface SourceConfig {
    fun value(key: String): String?
    fun secret(key: String): String?

    companion object {
        val EMPTY = MapSourceConfig()
    }
}

/** Size limits enforced before configuration values cross the runtime host boundary. */
object SourceHostConfigLimits {
    const val MAX_KEY_LENGTH: Int = 128
    const val MAX_VALUE_LENGTH: Int = 64 * 1024

    fun requireKey(key: String) {
        require(key.isNotBlank() && key.length <= MAX_KEY_LENGTH) {
            "Source config key must be non-blank and at most $MAX_KEY_LENGTH characters"
        }
        require('\r' !in key && '\n' !in key) {
            "Source config key must not contain CR or LF"
        }
    }

    fun requireValue(value: String?) {
        require(value == null || value.length <= MAX_VALUE_LENGTH) {
            "Source config value must be at most $MAX_VALUE_LENGTH characters"
        }
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

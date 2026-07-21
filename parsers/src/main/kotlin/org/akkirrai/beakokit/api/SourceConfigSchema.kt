package org.akkirrai.beakokit.api

import java.net.URI

enum class SourceConfigValueKind {
    TEXT,
    HTTPS_URL,
    HTTPS_URL_LIST,
    SECRET,
}

data class SourceConfigField(
    val key: String,
    val kind: SourceConfigValueKind,
    val required: Boolean = false,
    /** Optional closed set for a text-like field, useful for source modes and API versions. */
    val allowedValues: Set<String> = emptySet(),
    /** Stable, host-localisable label key; BeakoKit deliberately contains no UI strings. */
    val titleKey: String = key,
) {
    init {
        require(KEY_PATTERN.matches(key)) { "Invalid source config key: $key" }
        require(titleKey.isNotBlank()) { "Source config title key must not be blank" }
        require(kind != SourceConfigValueKind.SECRET || allowedValues.isEmpty()) {
            "Secret config fields cannot declare allowed values"
        }
        require(allowedValues.none(String::isBlank)) { "Source config allowed values must not be blank" }
    }

    private companion object {
        val KEY_PATTERN = Regex("[a-z][a-z0-9_]*")
    }
}

class SourceConfigSchema(fields: Iterable<SourceConfigField> = emptyList()) {
    val fields: List<SourceConfigField> = fields.toList()

    init {
        require(this.fields.map(SourceConfigField::key).distinct().size == this.fields.size) {
            "Source config schema contains duplicate keys"
        }
    }

    fun violations(config: SourceConfig): List<String> = buildList {
        fields.forEach { field ->
            val value = when (field.kind) {
                SourceConfigValueKind.SECRET -> config.secret(field.key)
                else -> config.value(field.key)
            }?.trim()
            if (field.required && value.isNullOrBlank()) {
                add("Missing required ${field.kind.name.lowercase()} config: ${field.key}")
            }
            if (!value.isNullOrBlank()) {
                when (field.kind) {
                    SourceConfigValueKind.HTTPS_URL -> if (!value.isHttpsUrl()) {
                        add("Config ${field.key} must use an HTTPS URL")
                    }
                    SourceConfigValueKind.HTTPS_URL_LIST -> if (!value.isHttpsUrlList()) {
                        add("Config ${field.key} must be a comma-separated list of HTTPS URLs")
                    }
                    else -> Unit
                }
                if (field.allowedValues.isNotEmpty() && value !in field.allowedValues) {
                    add("Config ${field.key} must be one of: ${field.allowedValues.sorted().joinToString()}")
                }
            }
        }
    }

    fun requireValid(config: SourceConfig) {
        val violations = violations(config)
        if (violations.isNotEmpty()) throw SourceConfigException(violations)
    }
}

private fun String.isHttpsUrl(): Boolean = runCatching {
    val uri = URI(this)
    uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
}.getOrDefault(false)

private fun String.isHttpsUrlList(): Boolean = split(',')
    .map(String::trim)
    .let { urls -> urls.isNotEmpty() && urls.all { url -> url.isNotEmpty() && url.isHttpsUrl() } }

class SourceConfigException(
    val violations: List<String>,
) : IllegalArgumentException(violations.joinToString(prefix = "Invalid source config: ", separator = "; "))

/** Optional contract for sources that declare their host-provided configuration. */
interface ConfigurableSource : AnimeSource {
    val configSchema: SourceConfigSchema
}

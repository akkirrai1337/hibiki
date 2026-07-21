package org.akkirrai.beakokit.api

enum class SourceConfigValueKind {
    TEXT,
    HTTPS_URL,
    SECRET,
}

data class SourceConfigField(
    val key: String,
    val kind: SourceConfigValueKind,
    val required: Boolean = false,
    /** Stable, host-localisable label key; BeakoKit deliberately contains no UI strings. */
    val titleKey: String = key,
) {
    init {
        require(KEY_PATTERN.matches(key)) { "Invalid source config key: $key" }
        require(titleKey.isNotBlank()) { "Source config title key must not be blank" }
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
            if (field.kind == SourceConfigValueKind.HTTPS_URL && !value.isNullOrBlank() && !value.startsWith("https://")) {
                add("Config ${field.key} must use an HTTPS URL")
            }
        }
    }

    fun requireValid(config: SourceConfig) {
        val violations = violations(config)
        if (violations.isNotEmpty()) throw SourceConfigException(violations)
    }
}

class SourceConfigException(
    val violations: List<String>,
) : IllegalArgumentException(violations.joinToString(prefix = "Invalid source config: ", separator = "; "))

/** Optional contract for sources that declare their host-provided configuration. */
interface ConfigurableSource : AnimeSource {
    val configSchema: SourceConfigSchema
}

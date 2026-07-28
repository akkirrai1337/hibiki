package org.akkirrai.beakokit.api

import kotlinx.serialization.Serializable

/** Lowercase BCP-47-compatible language tag used by source metadata. */
@JvmInline
@Serializable
value class SourceLanguage(val tag: String) {
    init {
        require(PATTERN.matches(tag)) { "Invalid source language tag: $tag" }
    }

    override fun toString(): String = tag

    companion object {
        private val PATTERN = Regex("[a-z]{2,3}(?:-[a-z0-9]{2,8})*")

        val RUSSIAN = SourceLanguage("ru")
        val ENGLISH = SourceLanguage("en")
    }
}

/** Platform-neutral network requirements that a host must explicitly approve. */
data class SourceNetworkRequirements(
    /** Hosts from which this source may intentionally return cleartext HTTP playback URLs. */
    val cleartextPlaybackHosts: Set<String> = emptySet(),
) {
    init {
        require(cleartextPlaybackHosts.all { host -> HOST_PATTERN.matches(host) }) {
            "Cleartext playback hosts must be lowercase host names"
        }
    }

    companion object {
        private val HOST_PATTERN = Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+")
    }
}

/** Platform-neutral metadata that can move with a source into a standalone repository. */
data class SourceInfo(
    val id: SourceId,
    val name: String,
    val languages: Set<SourceLanguage>,
    val primaryLanguage: SourceLanguage,
    val website: String? = null,
    /** HTTPS URL of an icon hosted by the source website. */
    val iconUrl: String? = null,
    val capabilities: Set<SourceCapability> = emptySet(),
    val networkRequirements: SourceNetworkRequirements = SourceNetworkRequirements(),
) {
    init {
        require(name.isNotBlank()) { "Source name must not be blank" }
        require(languages.isNotEmpty()) { "Source must declare at least one language" }
        require(primaryLanguage in languages) {
            "Primary source language must be included in supported languages: $primaryLanguage"
        }
        require(website == null || website.startsWith("https://")) {
            "Source website must use HTTPS: $website"
        }
        require(iconUrl == null || iconUrl.startsWith("https://")) {
            "Source icon URL must use HTTPS: $iconUrl"
        }
    }
}

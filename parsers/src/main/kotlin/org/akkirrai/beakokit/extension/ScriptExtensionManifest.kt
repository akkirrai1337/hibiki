package org.akkirrai.beakokit.extension

import kotlinx.serialization.Serializable
import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceInfo
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.SourceNetworkRequirements
import org.akkirrai.beakokit.model.AnimeSearchFilter
import org.akkirrai.beakokit.model.AnimeSearchSort

/**
 * A single-file manifest+payload describing a scripted (JS) anime source, loaded and executed at
 * runtime by [org.akkirrai.beakokit.extension.RhinoExtensionRuntime] instead of being compiled
 * into the app. Mirrors seanime's `extension.Extension` shape, scoped to what Hibiki needs today.
 */
@Serializable
data class ScriptExtensionManifest(
    val id: String,
    val name: String,
    val version: String,
    val author: String? = null,
    val website: String? = null,
    val iconUrl: String? = null,
    /** ISO 639-1 language tag, e.g. "ru" or "en". */
    val lang: String,
    /** Inline JS source implementing the `Provider` object. */
    val payload: String = "",
    val capabilities: Set<SourceCapability> = emptySet(),
    /** Host-declared, not content the script can claim for itself - drives the 18+ badge in the UI. */
    val isNsfw: Boolean = false,
    /** Hosts this extension's playback links are allowed to return over cleartext HTTP. */
    val cleartextPlaybackHosts: Set<String> = emptySet(),
    /**
     * Catalog sort/filter support - trusted, host-declared metadata (like [capabilities]), not
     * something the script can claim for itself. The actual filter option lists (available
     * genres, types, statuses) still come from the script's `getSettings()`, since those are
     * real content that can require a network call.
     */
    val supportedSorts: Set<AnimeSearchSort> = setOf(AnimeSearchSort.RELEVANCE),
    val supportedFilters: Set<AnimeSearchFilter> = emptySet(),
    val fallbackSort: AnimeSearchSort = AnimeSearchSort.RELEVANCE,
) {
    /**
     * The current [org.akkirrai.beakokit.extension.ScriptedAnimeSource] implementation always
     * implements [org.akkirrai.beakokit.api.LatestSource] and
     * [org.akkirrai.beakokit.api.PlaybackSource] unconditionally, so every manifest must declare
     * both capabilities today; per-extension capability variance is follow-up work.
     */
    fun violations(): List<String> = buildList {
        if (runCatching { SourceId(id) }.isFailure) {
            add("Extension id must be a lowercase slug: $id")
        }
        if (name.isBlank() || name.length > MAX_NAME_LENGTH) {
            add("Extension name must be 1-$MAX_NAME_LENGTH characters")
        }
        if (!SEMVER.matches(version)) {
            add("Extension version must be a basic semver string (x.y.z): $version")
        }
        if (runCatching { SourceLanguage(lang) }.isFailure) {
            add("Extension lang must be a valid BCP-47-compatible tag: $lang")
        }
        if (payload.isBlank()) {
            add("Extension payload must not be blank")
        }
        if (!capabilities.containsAll(REQUIRED_CAPABILITIES)) {
            add("Scripted sources currently require capabilities: ${REQUIRED_CAPABILITIES.joinToString()}")
        }
    }

    fun toSourceInfo(): SourceInfo = SourceInfo(
        id = SourceId(id),
        name = name,
        languages = setOf(SourceLanguage(lang)),
        primaryLanguage = SourceLanguage(lang),
        website = website,
        iconUrl = iconUrl,
        capabilities = capabilities,
        networkRequirements = SourceNetworkRequirements(cleartextPlaybackHosts = cleartextPlaybackHosts),
    )

    companion object {
        private const val MAX_NAME_LENGTH = 50
        private val SEMVER = Regex("""\d+\.\d+\.\d+""")
        private val REQUIRED_CAPABILITIES = setOf(SourceCapability.PLAYBACK, SourceCapability.LATEST_RELEASES)
    }
}

class ScriptExtensionValidationException(
    val extensionId: String,
    val violations: List<String>,
) : IllegalArgumentException(
    violations.joinToString(prefix = "Invalid script extension '$extensionId': ", separator = "; "),
)

/** A manifest that failed to load, kept alongside valid ones so a broken file never crashes the catalog. */
data class InvalidScriptExtension(
    val id: String,
    val path: String,
    val reason: String,
)

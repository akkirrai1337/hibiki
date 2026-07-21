package org.akkirrai.beakokit.testkit

import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.AnimeSearchFilter
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.SearchFilterOption
import org.akkirrai.beakokit.api.AnimeSource
import org.akkirrai.beakokit.api.HealthCheckSource
import org.akkirrai.beakokit.api.LatestSource
import org.akkirrai.beakokit.api.PlaybackGroup
import org.akkirrai.beakokit.api.PlaybackSource
import org.akkirrai.beakokit.api.SourceContractValidator
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.playback.PlaybackResolver
import org.akkirrai.beakokit.playback.ResolvedPlaybackStream
import kotlin.reflect.KClass

data class PlaybackContractSnapshot(
    val groups: List<PlaybackGroup>,
    val firstEpisodeLinks: List<PlayerLink>,
)

data class CatalogContractSnapshot(
    val searchResults: List<AnimeTitle>,
    val details: AnimeTitle,
)

data class PaginationContractSnapshot(
    val firstPage: List<AnimeTitle>,
    val secondPage: List<AnimeTitle>,
)

data class PlayableStreamContractSnapshot(
    val playback: PlaybackContractSnapshot,
    val resolved: ResolvedPlaybackStream,
)

/** Reusable contract assertions for fixture-backed source tests. */
object SourceTestKit {
    fun assertSourceContract(source: AnimeSource, expectedId: SourceId) {
        val violations = SourceContractValidator.violations(source)
        assertContract(violations.isEmpty()) {
            violations.joinToString(prefix = "Source contract violations: ", separator = "; ")
        }
        assertContract(source.info.id == expectedId) {
            "Expected source id $expectedId, got ${source.info.id}"
        }
    }

    /** Runs an opt-in lightweight source availability check without assuming every source supports it. */
    suspend fun assertHealthCheckContract(source: AnimeSource) {
        val healthCheck = source as? HealthCheckSource
            ?: throw AssertionError("${source.info.id} does not implement HealthCheckSource")
        healthCheck.checkHealth()
    }

    suspend fun assertCatalogContract(
        source: AnimeSource,
        request: AnimeSearchRequest,
    ): CatalogContractSnapshot {
        val results = assertSearchContract(source, request)
        assertContract(results.isNotEmpty()) {
            "Catalog fixture query returned no titles: ${request.query}"
        }
        val details = assertDetailsContract(source, results.first().id)
        return CatalogContractSnapshot(results, details)
    }

    suspend fun assertSearchContract(
        source: AnimeSource,
        request: AnimeSearchRequest,
    ): List<AnimeTitle> {
        assertContract(request.limit > 0) { "Search request limit must be positive" }
        val results = source.search(request)
        assertContract(results.size <= request.limit) {
            "Source returned ${results.size} search results for limit ${request.limit}"
        }
        assertTitles(results, "search results")
        return results
    }

    suspend fun assertEmptySearchContract(
        source: AnimeSource,
        request: AnimeSearchRequest,
    ) {
        val results = assertSearchContract(source, request)
        assertContract(results.isEmpty()) {
            "Expected an empty search page, got ${results.size} titles"
        }
    }

    suspend fun assertFilterCatalogContract(source: AnimeSource): AnimeSearchFilterCatalog {
        val catalog = source.getSearchFilterCatalog()
        val capabilities = source.catalogCapabilities
        assertContract(catalog.capabilities == capabilities) {
            "Filter catalog capabilities differ from source catalog capabilities"
        }
        assertOptions(catalog.sortOptions, "sort options")
        assertOptions(catalog.typeOptions, "type options")
        assertOptions(catalog.statusOptions, "status options")
        assertOptions(catalog.genreOptions, "genre options")
        assertContract(catalog.sortOptions.size >= capabilities.supportedSorts.size) {
            "Filter catalog exposes ${catalog.sortOptions.size} sort options for " +
                "${capabilities.supportedSorts.size} supported sorts"
        }
        assertContract(
            catalog.sortOptions.any { it.id.equals(capabilities.fallbackSort.name, ignoreCase = true) },
        ) {
            "Filter catalog does not expose fallback sort ${capabilities.fallbackSort}"
        }
        if (capabilities.supports(AnimeSearchFilter.TYPE)) {
            assertContract(catalog.typeOptions.isNotEmpty()) { "TYPE filter has no options" }
        }
        if (capabilities.supports(AnimeSearchFilter.STATUS)) {
            assertContract(catalog.statusOptions.isNotEmpty()) { "STATUS filter has no options" }
        }
        if (
            capabilities.supports(AnimeSearchFilter.INCLUDED_GENRES) ||
            capabilities.supports(AnimeSearchFilter.EXCLUDED_GENRES)
        ) {
            assertContract(catalog.genreOptions.isNotEmpty()) { "Genre filters have no options" }
        }
        return catalog
    }

    suspend fun assertFilteredSearchContract(
        source: AnimeSource,
        request: AnimeSearchRequest,
    ): List<AnimeTitle> {
        val catalog = assertFilterCatalogContract(source)
        val capabilities = source.catalogCapabilities
        assertContract(capabilities.supports(request.sort)) {
            "Source does not support requested sort ${request.sort}"
        }
        assertFilterAliases(
            request.typeAliases,
            catalog.typeOptions,
            AnimeSearchFilter.TYPE,
            capabilities.supports(AnimeSearchFilter.TYPE),
        )
        assertFilterAliases(
            request.statusAliases,
            catalog.statusOptions,
            AnimeSearchFilter.STATUS,
            capabilities.supports(AnimeSearchFilter.STATUS),
        )
        assertFilterAliases(
            request.includedGenreAliases,
            catalog.genreOptions,
            AnimeSearchFilter.INCLUDED_GENRES,
            capabilities.supports(AnimeSearchFilter.INCLUDED_GENRES),
        )
        assertFilterAliases(
            request.excludedGenreAliases.map { it.removePrefix("!") },
            catalog.genreOptions,
            AnimeSearchFilter.EXCLUDED_GENRES,
            capabilities.supports(AnimeSearchFilter.EXCLUDED_GENRES),
        )
        if (request.yearFrom != null || request.yearTo != null) {
            assertContract(capabilities.supports(AnimeSearchFilter.YEAR_RANGE)) {
                "Source does not support YEAR_RANGE"
            }
            assertContract(request.yearFrom == null || request.yearTo == null || request.yearFrom <= request.yearTo) {
                "yearFrom must not be greater than yearTo"
            }
        }
        return assertSearchContract(source, request)
    }

    suspend fun assertPaginationContract(
        source: AnimeSource,
        request: AnimeSearchRequest,
        pageSize: Int = request.limit,
    ): PaginationContractSnapshot {
        assertContract(pageSize > 0) { "Pagination page size must be positive" }
        assertContract(request.offset >= 0) { "Pagination offset must not be negative" }
        val firstPage = assertSearchContract(source, request.copy(limit = pageSize))
        val secondPage = assertSearchContract(
            source,
            request.copy(limit = pageSize, offset = request.offset + pageSize),
        )
        assertContract(firstPage.isNotEmpty()) { "Pagination fixture returned an empty first page" }
        assertContract(secondPage.isNotEmpty()) { "Pagination fixture returned an empty second page" }
        val duplicateIds = firstPage.map(AnimeTitle::id).toSet()
            .intersect(secondPage.map(AnimeTitle::id).toSet())
        assertContract(duplicateIds.isEmpty()) {
            "Adjacent pages contain duplicate ids: ${duplicateIds.joinToString()}"
        }
        return PaginationContractSnapshot(firstPage, secondPage)
    }

    suspend fun assertDetailsContract(source: AnimeSource, id: String): AnimeTitle {
        assertContract(id.isNotBlank()) { "Details id must not be blank" }
        val details = source.getById(id)
        assertTitle(details, "details")
        assertContract(details.id == id) {
            "Details changed opaque id from $id to ${details.id}"
        }
        return details
    }

    /** Verifies metadata fields that are optional in the core model but required by a given fixture. */
    suspend fun assertDetailsMetadataContract(
        source: AnimeSource,
        id: String,
        requireDescription: Boolean = false,
        requirePoster: Boolean = false,
    ): AnimeTitle {
        val details = assertDetailsContract(source, id)
        return assertTitleMetadataContract(details, requireDescription, requirePoster)
    }

    fun assertTitleMetadataContract(
        details: AnimeTitle,
        requireDescription: Boolean = false,
        requirePoster: Boolean = false,
    ): AnimeTitle {
        val description = details.description?.trim()
        val poster = details.posterUrl?.trim()
        assertContract(!requireDescription || !description.isNullOrBlank()) {
            "Details for ${details.id} must include a description"
        }
        assertContract(!requirePoster || !poster.isNullOrBlank()) {
            "Details for ${details.id} must include a poster URL"
        }
        return details
    }

    suspend fun assertLatestContract(source: AnimeSource, limit: Int): List<AnimeTitle> {
        assertContract(limit > 0) { "Latest limit must be positive" }
        val latest = source as? LatestSource
            ?: throw AssertionError("${source.info.id} does not implement LatestSource")
        val results = latest.latest(limit)
        assertContract(results.size <= limit) {
            "Source returned ${results.size} latest titles for limit $limit"
        }
        assertTitles(results, "latest titles")
        return results
    }

    suspend fun assertPlaybackContract(
        source: AnimeSource,
        title: AnimeTitle,
    ): PlaybackContractSnapshot {
        val playback = source as? PlaybackSource
            ?: throw AssertionError("${source.info.id} does not implement PlaybackSource")
        val groups = playback.getPlaybackGroups(title)
        assertContract(groups.isNotEmpty()) { "Playback source returned no groups" }
        assertContract(groups.map(PlaybackGroup::id).distinct().size == groups.size) {
            "Playback group ids must be unique within a title"
        }
        groups.forEach { group ->
            assertContract(group.id.isNotBlank()) { "Playback group id must not be blank" }
            assertContract(group.title.isNotBlank()) { "Playback group title must not be blank" }
            assertContract(group.episodes.isNotEmpty()) {
                "Playback group ${group.id} returned no episodes"
            }
            assertContract(group.episodes.all { it.id.isNotBlank() }) {
                "Episode ids must not be blank in playback group ${group.id}"
            }
            assertContract(group.episodes.map { it.id }.distinct().size == group.episodes.size) {
                "Episode ids must be unique within playback group ${group.id}"
            }
            assertContract(group.episodes.all { it.number.isFinite() }) {
                "Episode numbers must be finite in playback group ${group.id}"
            }
        }

        val firstGroup = groups.first()
        val links = playback.getPlayerLinks(title, firstGroup, firstGroup.episodes.first())
        assertContract(links.isNotEmpty()) { "Playback source returned no links for its first episode" }
        assertContract(links.all { it.url.isNotBlank() }) { "Player links must have non-blank URLs" }
        return PlaybackContractSnapshot(groups, links)
    }

    /** Resolves and validates a real playable stream from the first contract-valid episode. */
    suspend fun assertPlayableStreamContract(
        source: AnimeSource,
        title: AnimeTitle,
        resolver: PlaybackResolver,
    ): PlayableStreamContractSnapshot {
        val playback = assertPlaybackContract(source, title)
        val resolved = resolver.resolve(playback.firstEpisodeLinks)
        assertContract(resolved.validation.success) { "Playback resolver returned an unvalidated stream" }
        assertContract(resolved.validation.finalUrl.isNotBlank()) { "Resolved stream URL must not be blank" }
        return PlayableStreamContractSnapshot(playback, resolved)
    }

    /** Asserts a source or playback failure by its public exception type without hiding other failures. */
    suspend fun <T : Throwable> assertFailureContract(
        expectedType: KClass<T>,
        block: suspend () -> Unit,
    ): T {
        try {
            block()
        } catch (error: Throwable) {
            if (expectedType.isInstance(error)) {
                @Suppress("UNCHECKED_CAST")
                return error as T
            }
            throw AssertionError(
                "Expected ${expectedType.simpleName}, got ${error::class.simpleName}: ${error.message}",
                error,
            )
        }
        throw AssertionError("Expected ${expectedType.simpleName}, but operation completed successfully")
    }

    private fun assertTitles(titles: List<AnimeTitle>, label: String) {
        assertContract(titles.map(AnimeTitle::id).distinct().size == titles.size) {
            "Source returned duplicate ids in $label"
        }
        titles.forEach { assertTitle(it, label) }
    }

    private fun assertOptions(options: List<SearchFilterOption>, label: String) {
        assertContract(options.all { it.id.isNotBlank() }) { "Source returned a blank id in $label" }
        assertContract(options.all { it.title.isNotBlank() }) { "Source returned a blank title in $label" }
        assertContract(options.map(SearchFilterOption::id).distinct().size == options.size) {
            "Source returned duplicate ids in $label"
        }
    }

    private fun assertFilterAliases(
        aliases: List<String>,
        options: List<SearchFilterOption>,
        filter: AnimeSearchFilter,
        supported: Boolean,
    ) {
        if (aliases.isEmpty()) return
        assertContract(supported) { "Source does not support $filter" }
        val knownAliases = options.map { it.id.lowercase() }.toSet()
        val unknownAliases = aliases.map(String::trim)
            .filterNot { it.lowercase() in knownAliases }
        assertContract(unknownAliases.isEmpty()) {
            "Unknown $filter aliases: ${unknownAliases.joinToString()}"
        }
    }

    private fun assertTitle(title: AnimeTitle, label: String) {
        assertContract(title.id.isNotBlank()) { "Source returned a blank title id in $label" }
        assertContract(title.displayName.isNotBlank()) {
            "Source returned a blank display name for ${title.id} in $label"
        }
        assertContract(title.description == null || title.description.isNotBlank()) {
            "Source returned a blank description for ${title.id} in $label"
        }
        assertContract(title.posterUrl == null || title.posterUrl.isNotBlank()) {
            "Source returned a blank poster URL for ${title.id} in $label"
        }
    }

    private inline fun assertContract(condition: Boolean, lazyMessage: () -> String) {
        if (!condition) throw AssertionError(lazyMessage())
    }
}

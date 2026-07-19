package org.akkirrai.beakokit.testkit

import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.api.AnimeSource
import org.akkirrai.beakokit.api.LatestSource
import org.akkirrai.beakokit.api.PlaybackGroup
import org.akkirrai.beakokit.api.PlaybackSource
import org.akkirrai.beakokit.api.SourceContractValidator
import org.akkirrai.beakokit.api.SourceId

data class PlaybackContractSnapshot(
    val groups: List<PlaybackGroup>,
    val firstEpisodeLinks: List<PlayerLink>,
)

data class CatalogContractSnapshot(
    val searchResults: List<AnimeTitle>,
    val details: AnimeTitle,
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

    suspend fun assertDetailsContract(source: AnimeSource, id: String): AnimeTitle {
        assertContract(id.isNotBlank()) { "Details id must not be blank" }
        val details = source.getById(id)
        assertTitle(details, "details")
        assertContract(details.id == id) {
            "Details changed opaque id from $id to ${details.id}"
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

    private fun assertTitles(titles: List<AnimeTitle>, label: String) {
        assertContract(titles.map(AnimeTitle::id).distinct().size == titles.size) {
            "Source returned duplicate ids in $label"
        }
        titles.forEach { assertTitle(it, label) }
    }

    private fun assertTitle(title: AnimeTitle, label: String) {
        assertContract(title.id.isNotBlank()) { "Source returned a blank title id in $label" }
        assertContract(title.displayName.isNotBlank()) {
            "Source returned a blank display name for ${title.id} in $label"
        }
    }

    private inline fun assertContract(condition: Boolean, lazyMessage: () -> String) {
        if (!condition) throw AssertionError(lazyMessage())
    }
}

package org.akkirrai.beakokit.testkit

import org.akkirrai.animeresolver.model.AnimeTitle
import org.akkirrai.animeresolver.model.PlayerLink
import org.akkirrai.beakokit.api.AnimeSource
import org.akkirrai.beakokit.api.PlaybackGroup
import org.akkirrai.beakokit.api.PlaybackSource
import org.akkirrai.beakokit.api.SourceContractValidator
import org.akkirrai.beakokit.api.SourceId

data class PlaybackContractSnapshot(
    val groups: List<PlaybackGroup>,
    val firstEpisodeLinks: List<PlayerLink>,
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

    private inline fun assertContract(condition: Boolean, lazyMessage: () -> String) {
        if (!condition) throw AssertionError(lazyMessage())
    }
}

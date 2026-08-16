package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.akkirrai.hibiki.shared.player.model.PlaybackSettingsOptions
import org.akkirrai.hibiki.shared.player.model.WatchEpisode
import org.akkirrai.hibiki.shared.player.model.WatchSource

class HibikiPlaybackRequestPreparationTest {
    @Test
    fun playbackSelectionUsesSourceIdentityAndResolvedPreferences() {
        val selection = createPlaybackSelection(
            titleId = "anime",
            source = WatchSource("source", "Dub", 12),
            quality = "1080p",
            playerName = "Player",
        )

        assertEquals("anime", selection.titleId)
        assertEquals("source", selection.sourceId)
        assertEquals("Dub", selection.sourceTitle)
        assertEquals("1080p", selection.quality)
        assertEquals("Player", selection.playerName)
    }

    @Test
    fun preparationBuildsContextFromExplicitSourceAndPreferences() {
        val source = WatchSource("source", "Dub", 12, qualityLabel = "1080p")
        val episode = WatchEpisode("episode", 3.0, "Episode 3")

        val prepared = preparePlaybackRequest(
            titleId = "anime",
            episode = episode,
            selectedSource = null,
            sourceOverride = source,
            preferredPlayerName = "Player",
            preferredQuality = "720p",
            savedSelection = null,
            allowSavedSelection = false,
            episodes = listOf(episode),
            settingsOptions = PlaybackSettingsOptions(),
        )

        requireNotNull(prepared)
        assertEquals("source", prepared.context.sourceId)
        assertEquals("Player", prepared.context.selectedPlayerName)
        assertEquals("720p", prepared.context.selectedQualityLabel)
    }

    @Test
    fun preparationReturnsNullWithoutAnySource() {
        assertNull(
            preparePlaybackRequest(
                titleId = "anime",
                episode = WatchEpisode("episode", 1.0, null),
                selectedSource = null,
                sourceOverride = null,
                preferredPlayerName = null,
                preferredQuality = null,
                savedSelection = null,
                allowSavedSelection = true,
                episodes = emptyList(),
                settingsOptions = PlaybackSettingsOptions(),
            ),
        )
    }
}

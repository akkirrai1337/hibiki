package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.model.WatchEpisode

class PlayerEpisodeSelectionActionsTest {
    @Test
    fun dispatchesControlsProgressAndSelectionInOrder() {
        val episode = WatchEpisode(id = "episode-2", number = 2.0, title = "Episode 2")
        val events = mutableListOf<String>()

        dispatchPlayerEpisodeSelection(
            episode = episode,
            setControlsVisible = { events += "controls" },
            persistProgress = { events += "progress" },
            onEpisodeSelected = { events += "selected:${it.id}" },
        )

        assertEquals(listOf("controls", "progress", "selected:episode-2"), events)
    }

    @Test
    fun adjacentDispatchReturnsFalseWithoutChangingStateWhenMissing() {
        val events = mutableListOf<String>()

        val dispatched = dispatchAdjacentPlayerEpisodeSelection(
            episodes = listOf(WatchEpisode(id = "episode-1", number = 1.0, title = "Episode 1")),
            currentEpisodeId = "episode-1",
            currentEpisodeNumber = 1.0,
            offset = 1,
            setControlsVisible = { events += "controls" },
            persistProgress = { events += "progress" },
            onEpisodeSelected = { events += "selected:${it.id}" },
        )

        assertEquals(false, dispatched)
        assertEquals(emptyList(), events)
    }

    @Test
    fun closePersistsBeforeBack() {
        val events = mutableListOf<String>()

        dispatchPlayerClose(
            persistProgress = { events += "progress" },
            onBack = { events += "back" },
        )

        assertEquals(listOf("progress", "back"), events)
    }

    @Test
    fun settingsActionMakesControlsVisibleBeforePersistenceAndDispatch() {
        val events = mutableListOf<String>()

        dispatchPlayerSettingsAction(
            action = PlaybackSettingsAction.SetAutoSkipSegments(true),
            setControlsVisible = { events += "controls" },
            persistProgress = { events += "progress" },
            onSettingsAction = { events += "settings" },
        )

        assertEquals(listOf("controls", "progress", "settings"), events)
    }
}

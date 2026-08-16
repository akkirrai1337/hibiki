package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerOverlayVisibilityPolicyTest {
    @Test
    fun skipPromptIsVisibleOnlyWithInteractiveControls() {
        assertTrue(
            shouldShowSkipSegmentPrompt(
                controlsVisible = true,
                playerLocked = false,
                playlistVisible = false,
                settingsVisible = false,
            ),
        )
    }

    @Test
    fun skipPromptHidesForLockAndPlaybackOverlays() {
        val cases = listOf(
            Triple(true, true, false),
            Triple(true, false, true),
            Triple(false, false, false),
        )

        cases.forEach { (locked, playlist, controlsVisible) ->
            assertFalse(
                shouldShowSkipSegmentPrompt(
                    controlsVisible = controlsVisible,
                    playerLocked = locked,
                    playlistVisible = playlist,
                    settingsVisible = false,
                ),
            )
        }
    }
}

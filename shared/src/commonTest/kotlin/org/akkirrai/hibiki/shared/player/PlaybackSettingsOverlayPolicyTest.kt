package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.akkirrai.hibiki.shared.model.WatchSource

class PlaybackSettingsOverlayPolicyTest {
    @Test
    fun selectionActionsDismissSettingsOverlay() {
        assertTrue(shouldDismissPlayerSettingsForAction(PlaybackSettingsAction.SelectVoiceover(WatchSource("source", "Dub", 12))))
        assertTrue(shouldDismissPlayerSettingsForAction(PlaybackSettingsAction.SelectPlayer("Media3")))
        assertTrue(shouldDismissPlayerSettingsForAction(PlaybackSettingsAction.SelectQuality("1080p")))
    }

    @Test
    fun toggleActionsKeepSettingsOverlayOpen() {
        assertFalse(shouldDismissPlayerSettingsForAction(PlaybackSettingsAction.SetAutoSkipSegments(true)))
        assertFalse(shouldDismissPlayerSettingsForAction(PlaybackSettingsAction.SetAutoPlayNextEpisode(true)))
    }
}

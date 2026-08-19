package org.akkirrai.hibiki.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.akkirrai.hibiki.player.model.WatchSource

class PlaybackSettingsOverlayPolicyTest {
    @Test
    fun allActionsKeepSettingsOverlayOpen() {
        assertFalse(shouldDismissPlayerSettingsForAction(PlaybackSettingsAction.SelectVoiceover(WatchSource("source", "Dub", 12))))
        assertFalse(shouldDismissPlayerSettingsForAction(PlaybackSettingsAction.SelectPlayer("Media3")))
        assertFalse(shouldDismissPlayerSettingsForAction(PlaybackSettingsAction.SelectQuality("1080p")))
    }

    @Test
    fun toggleActionsKeepSettingsOverlayOpen() {
        assertFalse(shouldDismissPlayerSettingsForAction(PlaybackSettingsAction.SetAutoSkipSegments(true)))
        assertFalse(shouldDismissPlayerSettingsForAction(PlaybackSettingsAction.SetAutoPlayNextEpisode(true)))
    }
}

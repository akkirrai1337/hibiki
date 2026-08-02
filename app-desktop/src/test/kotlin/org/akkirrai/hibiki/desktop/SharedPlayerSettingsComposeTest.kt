package org.akkirrai.hibiki.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.akkirrai.hibiki.shared.model.PlaybackSettingsOptions
import org.akkirrai.hibiki.shared.model.WatchSource
import org.akkirrai.hibiki.shared.navigation.AppPlayerSettingsDestination
import org.akkirrai.hibiki.shared.player.AppPlayerSettingsContent
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SharedPlayerSettingsComposeTest {
    @Test
    fun voiceoverChoiceDeliversCommonPlayerSettingsAction() = runComposeUiTest {
        val source = WatchSource("source:ani-liberty", "AniLiberty", 12)
        var selectedSource: WatchSource? = null

        setContent {
            MaterialTheme {
                AppPlayerSettingsContent(
                    destination = AppPlayerSettingsDestination.Voiceover,
                    selectedSpeed = 1f,
                    selectedSourceId = "source:yummy-anime",
                    selectedPlayerName = null,
                    selectedQualityLabel = null,
                    availableQualityLabels = emptyList(),
                    autoSkipSegments = false,
                    autoPlayNextEpisode = true,
                    options = PlaybackSettingsOptions(voiceovers = listOf(source)),
                    onNavigate = {},
                    onBack = {},
                    backHandler = { _, _ -> },
                    onSelectSpeed = {},
                    onSelectVoiceover = { selectedSource = it },
                    onSelectPlayer = {},
                    onSelectQuality = {},
                    onAutoSkipSegmentsChange = {},
                    onAutoPlayNextEpisodeChange = {},
                )
            }
        }

        onNodeWithText("AniLiberty")
            .assertIsDisplayed()
            .performClick()

        assertEquals(source, selectedSource)
    }
}

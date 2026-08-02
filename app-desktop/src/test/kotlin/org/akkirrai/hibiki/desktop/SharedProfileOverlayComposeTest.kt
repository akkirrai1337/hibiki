package org.akkirrai.hibiki.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.profile.AppLocalProfileLabels
import org.akkirrai.hibiki.shared.profile.AppLocalProfileScreen
import org.akkirrai.hibiki.shared.profile.LocalProfileSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SharedProfileOverlayComposeTest {
    @Test
    fun commonProfileBridgesSettingsAndAvatarActions() = runComposeUiTest {
        var settingsClicks = 0
        var avatarEditClicks = 0

        setContent {
            MaterialTheme {
                AppLocalProfileScreen(
                    snapshot = LocalProfileSnapshot(
                        watchTimeLabel = "0h",
                        activeDaysCount = 0,
                        totalEpisodes = 0,
                        libraryTotal = 0,
                        libraryStatusSegments = emptyList(),
                        activityDays = emptyList(),
                        recentLibraryItems = emptyList(),
                        favoriteLibraryItems = emptyList(),
                        genreSegments = emptyList(),
                        genreTrackedTitlesCount = 0,
                    ),
                    profileName = "Hibiki",
                    isEditing = true,
                    editedName = "Hibiki",
                    bottomContentPadding = 24.dp,
                    labels = profileLabels(),
                    onNameChange = {},
                    onAvatarEditClick = { avatarEditClicks++ },
                    onEditActionClick = {},
                    onSettingsClick = { settingsClicks++ },
                    avatarContent = { _: Modifier -> },
                )
            }
        }

        onNodeWithContentDescription("Settings")
            .performClick()
        onNodeWithContentDescription("Change avatar")
            .performClick()

        assertEquals(1, settingsClicks)
        assertEquals(1, avatarEditClicks)
    }

    private fun profileLabels() = AppLocalProfileLabels(
        overviewTab = "Overview",
        activityTab = "Activity",
        favoritesTab = "Favorites",
        profileNameLabel = "Profile name",
        editContentDescription = "Edit",
        saveContentDescription = "Save",
        changeAvatarContentDescription = "Change avatar",
        settingsContentDescription = "Settings",
        totalLabel = "Total",
        daysLabel = "Days",
        timeLabel = "Time",
        recentTitle = "Recent",
        recentEmptyText = "No recent",
        favoritesEmptyText = "No favorites",
        analyticsWatchTitle = "Watch time",
        analyticsTotalLabel = "Total",
        analyticsGenresTitle = "Genres",
        analyticsGenresLabel = "Genre",
        analyticsTitle = "Analytics",
        episodesStatLabel = "Episodes",
        watchStatLabel = "Watched",
        activityTitle = "Activity",
    )
}

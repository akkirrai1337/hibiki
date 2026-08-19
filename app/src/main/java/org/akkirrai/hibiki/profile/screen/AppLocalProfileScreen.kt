package org.akkirrai.hibiki.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class AppLocalProfileLabels(
    val overviewTab: String,
    val activityTab: String,
    val favoritesTab: String,
    val profileNameLabel: String,
    val editContentDescription: String,
    val saveContentDescription: String,
    val changeAvatarContentDescription: String,
    val settingsContentDescription: String,
    val totalLabel: String,
    val daysLabel: String,
    val timeLabel: String,
    val recentTitle: String,
    val recentEmptyText: String,
    val favoritesEmptyText: String,
    val analyticsWatchTitle: String,
    val analyticsTotalLabel: String,
    val analyticsGenresTitle: String,
    val analyticsGenresLabel: String,
    val analyticsTitle: String,
    val episodesStatLabel: String,
    val watchStatLabel: String,
    val activityTitle: String,
)

@Composable
fun AppLocalProfileScreen(
    snapshot: LocalProfileSnapshot,
    profileName: String,
    isEditing: Boolean,
    editedName: String,
    bottomContentPadding: Dp,
    isLoading: Boolean = false,
    avatarEditAvailable: Boolean = true,
    labels: AppLocalProfileLabels,
    onNameChange: (String) -> Unit,
    onAvatarEditClick: () -> Unit,
    onEditActionClick: () -> Unit,
    onSettingsClick: () -> Unit,
    avatarContent: @Composable BoxScope.(Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isLoading) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        ) {
            CircularProgressIndicator()
        }
        return
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        AppProfileBannerLayout(
            banner = { ratio, bannerModifier ->
                Box(modifier = bannerModifier) {
                        ProfileAvatar(
                            ratio = ratio,
                            isEditing = isEditing && avatarEditAvailable,
                        editContentDescription = labels.changeAvatarContentDescription,
                        onEditClick = onAvatarEditClick,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        avatarContent = { avatarModifier ->
                            avatarContent(avatarModifier)
                        },
                    )
                }
            },
            bannerElevatedContent = { collapseRatio, actionModifier ->
                // As the banner collapses (ratio 1 -> 0, avatar scrolling out of view), slide
                // these buttons down from their expanded position (overlapping the avatar) to
                // roughly the nickname's height, instead of staying pinned near the status bar.
                val elevatedActionsOffset = ProfileHeaderActionsCollapsedOffsetY * (1f - collapseRatio) +
                    ProfileHeaderActionsExpandedOffsetY * collapseRatio
                Row(
                    modifier = actionModifier.offset(y = elevatedActionsOffset),
                    horizontalArrangement = Arrangement.spacedBy(ProfileSmallPadding),
                ) {
                    ProfileEditActionButton(
                        isEditing = isEditing,
                        contentDescription = if (isEditing) labels.saveContentDescription else labels.editContentDescription,
                        onClick = onEditActionClick,
                    )
                    ProfileSettingsActionButton(
                        contentDescription = labels.settingsContentDescription,
                        onClick = onSettingsClick,
                    )
                }
            },
            contentBackgroundColor = MaterialTheme.colorScheme.surfaceContainer,
            bannerBackgroundColor = MaterialTheme.colorScheme.surfaceContainer,
            contentPadding = PaddingValues(top = ProfileLargePadding / 2),
            content = {
                AppProfileIdentityTabs(
                    profileName = profileName,
                    isEditing = isEditing,
                    horizontalPadding = ProfileLargePadding,
                    tabTitles = listOf(labels.overviewTab, labels.activityTab, labels.favoritesTab),
                    nameEditorContent = {
                        ProfileNameEditor(
                            label = labels.profileNameLabel,
                            name = editedName,
                            onNameChange = onNameChange,
                        )
                    },
                    pageContent = { page ->
                        when (page) {
                            0 -> AppProfileOverviewContent(
                                snapshot = snapshot,
                                bottomContentPadding = bottomContentPadding,
                                totalLabel = labels.totalLabel,
                                daysLabel = labels.daysLabel,
                                timeLabel = labels.timeLabel,
                                recentContent = {
                                    AppProfileRecentLibraryContent(
                                        items = snapshot.recentLibraryItems,
                                        title = labels.recentTitle,
                                        emptyText = labels.recentEmptyText,
                                    )
                                },
                            )
                            1 -> ProfileScrollableTab(bottomContentPadding = bottomContentPadding) {
                                AppProfileAnalyticsContent(
                                    snapshot = snapshot,
                                    watchTimeTitle = labels.analyticsWatchTitle,
                                    totalLabel = labels.analyticsTotalLabel,
                                    genresTitle = labels.analyticsGenresTitle,
                                    genresLabel = labels.analyticsGenresLabel,
                                    analyticsTitle = labels.analyticsTitle,
                                    episodeStat = "${labels.episodesStatLabel}: ${snapshot.totalEpisodes}",
                                    watchStat = "${labels.watchStatLabel}: ${snapshot.watchTimeLabel}",
                                    activityTitle = labels.activityTitle,
                                    activeColor = if (snapshot.activeDaysCount > 0) {
                                        Color(0xFFFF7A86)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.34f)
                                    },
                                    inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.54f),
                                )
                            }
                            else -> AppProfileFavoritesTab(
                                isEmpty = snapshot.favoriteLibraryItems.isEmpty(),
                                bottomContentPadding = bottomContentPadding,
                                emptyContent = {
                                    androidx.compose.material3.Text(
                                        text = labels.favoritesEmptyText,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                            ) {
                                AppProfileRecentLibraryContent(
                                    items = snapshot.favoriteLibraryItems,
                                    title = null,
                                    emptyText = labels.favoritesEmptyText,
                                )
                            }
                        }
                    },
                )
            },
        )
    }
}

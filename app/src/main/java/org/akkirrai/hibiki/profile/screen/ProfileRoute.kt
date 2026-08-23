package org.akkirrai.hibiki.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.library.LibraryCategory
import org.akkirrai.hibiki.profile.AppLocalProfileLabels
import org.akkirrai.hibiki.profile.AppLocalProfileScreen
import org.akkirrai.hibiki.profile.LocalProfileData
import org.akkirrai.hibiki.profile.ProfileAvatarImage
import org.akkirrai.hibiki.profile.ProfileAvatarPlaceholder
import org.akkirrai.hibiki.profile.LocalProfileSnapshotLabels
import org.akkirrai.hibiki.profile.buildLocalProfileSnapshot
import org.akkirrai.hibiki.profile.defaultProfileActivityDateStrings
import org.akkirrai.hibiki.profile.profileActivityDateLabel
import org.akkirrai.hibiki.profile.profileRecentDateLabel
import org.akkirrai.hibiki.profile.formatDurationHours
import org.akkirrai.hibiki.app.settings.LanguageMode
import org.akkirrai.hibiki.app.settings.resolveAppLanguageTag
import org.akkirrai.hibiki.text.AppTextKey
import org.akkirrai.hibiki.text.appText

data class ProfileScreenState(
    val data: LocalProfileData,
    val isEditing: Boolean,
    val editedName: String,
    val isLoading: Boolean,
    val avatarEditAvailable: Boolean,
)

data class ProfileScreenActions(
    val onNameChange: (String) -> Unit,
    val onEditClick: () -> Unit,
    val onSaveClick: () -> Unit,
    val onSettingsClick: () -> Unit,
    val onAvatarEdit: (((String) -> Unit) -> Unit),
    val onAvatarPicked: (String) -> Unit,
)

@Composable
internal fun ProfileRoute(
    state: ProfileScreenState,
    actions: ProfileScreenActions,
    languageMode: LanguageMode,
    systemLanguage: String,
    bottomContentPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val profileDateTodayLabel = appText(AppTextKey.ProfileDateToday)
    val profileDateYesterdayLabel = appText(AppTextKey.ProfileDateYesterday)
    val profileDateDaysAgoTemplate = appText(AppTextKey.ProfileDateDaysAgo)
    val categoryLabels = mapOf(
        LibraryCategory.Watching to appText(AppTextKey.LibraryWatching),
        LibraryCategory.Planned to appText(AppTextKey.LibraryPlanned),
        LibraryCategory.Completed to appText(AppTextKey.LibraryCompleted),
        LibraryCategory.Dropped to appText(AppTextKey.LibraryDropped),
        LibraryCategory.OnHold to appText(AppTextKey.LibraryOnHold),
        LibraryCategory.Favorite to appText(AppTextKey.LibraryFavorite),
        LibraryCategory.Saved to appText(AppTextKey.LibrarySaved),
    )
    val snapshot = buildLocalProfileSnapshot(
        data = state.data,
        activityDateStrings = defaultProfileActivityDateStrings(),
        labels = LocalProfileSnapshotLabels(
            durationLabel = { duration -> "${formatDurationHours(duration)} h" },
            categoryLabel = { category -> categoryLabels.getValue(category) },
            dateLabel = { value ->
                profileRecentDateLabel(
                    value = value,
                    languageTag = resolveAppLanguageTag(languageMode, systemLanguage),
                    todayLabel = profileDateTodayLabel,
                    yesterdayLabel = profileDateYesterdayLabel,
                    daysAgoLabel = { days -> profileDateDaysAgoTemplate.replace("%d", days.toString()) },
                )
            },
            activityDateLabel = ::profileActivityDateLabel,
        ),
    )
    AppLocalProfileScreen(
        snapshot = snapshot,
        profileName = state.data.profileName.ifBlank { appText(AppTextKey.AppName) },
        isLoading = state.isLoading,
        avatarEditAvailable = state.avatarEditAvailable,
        isEditing = state.isEditing,
        editedName = state.editedName,
        bottomContentPadding = bottomContentPadding,
        labels = AppLocalProfileLabels(
            overviewTab = appText(AppTextKey.ProfileTabOverview),
            activityTab = appText(AppTextKey.ProfileTabActivity),
            favoritesTab = appText(AppTextKey.ProfileTabFavorites),
            profileNameLabel = appText(AppTextKey.ProfileName),
            editContentDescription = appText(AppTextKey.ProfileEdit),
            saveContentDescription = appText(AppTextKey.ProfileSave),
            changeAvatarContentDescription = appText(AppTextKey.ProfileChangeAvatar),
            settingsContentDescription = appText(AppTextKey.Settings),
            totalLabel = appText(AppTextKey.ProfileStatTotal),
            daysLabel = appText(AppTextKey.ProfileStatDays),
            timeLabel = appText(AppTextKey.ProfileStatTime),
            recentTitle = appText(AppTextKey.ProfileRecent),
            recentEmptyText = appText(AppTextKey.ProfileEmptyRecent),
            favoritesEmptyText = appText(AppTextKey.ProfileEmptyFavorites),
            analyticsWatchTitle = appText(AppTextKey.ProfileAnalyticsWatchTime),
            analyticsTotalLabel = appText(AppTextKey.ProfileAnalyticsTotal),
            analyticsGenresTitle = appText(AppTextKey.ProfileAnalyticsGenres),
            analyticsGenresLabel = appText(AppTextKey.ProfileAnalyticsGenresLabel),
            analyticsTitle = appText(AppTextKey.Profile),
            episodesStatLabel = appText(AppTextKey.ProfileEpisodes),
            watchStatLabel = appText(AppTextKey.ProfileAnalyticsWatched),
            activityTitle = appText(AppTextKey.ProfileActivity),
        ),
        onNameChange = actions.onNameChange,
        onAvatarEditClick = { actions.onAvatarEdit(actions.onAvatarPicked) },
        onEditActionClick = if (state.isEditing) actions.onSaveClick else actions.onEditClick,
        onSettingsClick = actions.onSettingsClick,
        avatarContent = { avatarModifier ->
            state.data.profileAvatarUri?.let { ProfileAvatarImage(it) }
                ?: ProfileAvatarPlaceholder(avatarModifier)
        },
        modifier = modifier,
    )
}

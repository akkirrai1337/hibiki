package org.akkirrai.hibiki.shared.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.profile.AppLocalProfileLabels
import org.akkirrai.hibiki.shared.profile.AppLocalProfileScreen
import org.akkirrai.hibiki.shared.profile.LocalProfileData
import org.akkirrai.hibiki.shared.profile.ProfileAvatarImage
import org.akkirrai.hibiki.shared.profile.ProfileAvatarPlaceholder
import org.akkirrai.hibiki.shared.profile.LocalProfileSnapshotLabels
import org.akkirrai.hibiki.shared.profile.buildLocalProfileSnapshot
import org.akkirrai.hibiki.shared.profile.defaultProfileActivityDateStrings
import org.akkirrai.hibiki.shared.profile.profileActivityDateLabel
import org.akkirrai.hibiki.shared.profile.profileRecentDateLabel
import org.akkirrai.hibiki.shared.profile.formatDurationHours
import org.akkirrai.hibiki.shared.settings.LanguageMode
import org.akkirrai.hibiki.shared.settings.resolveAppLanguageTag
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText

@Composable
internal fun ProfileDestinationContent(
    profileData: LocalProfileData,
    profileLoading: Boolean,
    profileAvatarEditAvailable: Boolean,
    isEditingProfile: Boolean,
    editedProfileName: String,
    languageMode: LanguageMode,
    systemLanguage: String,
    bottomContentPadding: Dp,
    onProfileNameChange: (String) -> Unit,
    onProfileEditClick: () -> Unit,
    onProfileSaveClick: () -> Unit,
    onProfileSettingsClick: () -> Unit,
    onProfileAvatarEdit: (((String) -> Unit) -> Unit),
    onProfileAvatarPicked: (String) -> Unit,
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
        data = profileData,
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
        profileName = profileData.profileName.ifBlank { appText(AppTextKey.AppName) },
        isLoading = profileLoading,
        avatarEditAvailable = profileAvatarEditAvailable,
        isEditing = isEditingProfile,
        editedName = editedProfileName,
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
        onNameChange = onProfileNameChange,
        onAvatarEditClick = { onProfileAvatarEdit(onProfileAvatarPicked) },
        onEditActionClick = if (isEditingProfile) onProfileSaveClick else onProfileEditClick,
        onSettingsClick = onProfileSettingsClick,
        avatarContent = { avatarModifier ->
            profileData.profileAvatarUri?.let { ProfileAvatarImage(it) }
                ?: ProfileAvatarPlaceholder(avatarModifier)
        },
        modifier = modifier,
    )
}

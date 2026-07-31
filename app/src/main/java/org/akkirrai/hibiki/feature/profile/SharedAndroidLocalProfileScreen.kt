package org.akkirrai.hibiki.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.LocalAppLanguage
import org.akkirrai.hibiki.app.settings.withLanguage
import org.akkirrai.hibiki.shared.profile.AppLocalProfileLabels
import org.akkirrai.hibiki.shared.profile.AppLocalProfileScreen
import org.akkirrai.hibiki.shared.profile.ProfileAvatarImage
import org.akkirrai.hibiki.shared.profile.ProfileAvatarPlaceholder

@Composable
fun SharedAndroidLocalProfileScreen(
    onSettingsClick: () -> Unit,
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
    viewModel: LocalProfileViewModel = viewModel(factory = LocalProfileViewModel.Factory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsState()
    var isEditingProfile by remember { mutableStateOf(false) }
    var editedName by remember(state.data.profileName) { mutableStateOf(state.data.profileName) }
    val context = LocalContext.current
    val appLanguage = LocalAppLanguage.current
    val localizedResources = remember(context, appLanguage) {
        context.withLanguage(appLanguage).resources
    }
    val snapshot = remember(localizedResources, state.data) {
        buildProfileSnapshot(localizedResources, state.data)
    }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.updateProfileAvatar(it.toString()) }
    }

    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    AppLocalProfileScreen(
        snapshot = snapshot,
        profileName = state.data.profileName,
        isLoading = state.isLoading,
        isEditing = isEditingProfile,
        editedName = editedName,
        bottomContentPadding = bottomContentPadding,
        labels = sharedAndroidProfileLabels(),
        onNameChange = { editedName = it },
        onAvatarEditClick = { avatarPicker.launch(arrayOf("image/*")) },
        onEditActionClick = {
            if (isEditingProfile) viewModel.updateProfileName(editedName)
            else editedName = state.data.profileName
            isEditingProfile = !isEditingProfile
        },
        onSettingsClick = onSettingsClick,
        avatarContent = { avatarModifier ->
            if (state.data.profileAvatarUri.isNullOrBlank()) {
                ProfileAvatarPlaceholder(modifier = avatarModifier)
            } else {
                ProfileAvatarImage(url = state.data.profileAvatarUri.orEmpty())
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun sharedAndroidProfileLabels() = AppLocalProfileLabels(
    overviewTab = stringResource(R.string.local_profile_tab_overview),
    activityTab = stringResource(R.string.local_profile_tab_activity),
    favoritesTab = stringResource(R.string.local_profile_tab_favorites),
    profileNameLabel = stringResource(R.string.local_profile_name),
    editContentDescription = stringResource(R.string.local_profile_edit),
    saveContentDescription = stringResource(R.string.action_save),
    changeAvatarContentDescription = stringResource(R.string.local_profile_change_avatar),
    settingsContentDescription = stringResource(R.string.local_profile_settings),
    totalLabel = stringResource(R.string.local_profile_stat_total),
    daysLabel = stringResource(R.string.local_profile_stat_days),
    timeLabel = stringResource(R.string.local_profile_stat_time),
    recentTitle = stringResource(R.string.yummy_account_recent_additions_title),
    recentEmptyText = stringResource(R.string.yummy_account_recent_library_empty),
    favoritesEmptyText = stringResource(R.string.local_profile_empty_favorites),
    analyticsWatchTitle = stringResource(R.string.local_profile_analytics_watch_title),
    analyticsTotalLabel = stringResource(R.string.local_profile_analytics_total_label),
    analyticsGenresTitle = stringResource(R.string.local_profile_analytics_genres_title),
    analyticsGenresLabel = stringResource(R.string.local_profile_analytics_genres_label),
    analyticsTitle = stringResource(R.string.yummy_account_segment_stats),
    episodesStatLabel = stringResource(R.string.yummy_account_stat_episodes_title),
    watchStatLabel = stringResource(R.string.yummy_account_stat_watch_short),
    activityTitle = stringResource(R.string.yummy_account_activity_title),
)

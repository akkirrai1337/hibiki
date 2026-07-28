package org.akkirrai.hibiki.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.LocalAppLanguage
import org.akkirrai.hibiki.app.settings.withLanguage
import org.akkirrai.hibiki.shared.profile.ProfileEditActionButton
import org.akkirrai.hibiki.shared.profile.ProfileSettingsActionButton
import org.akkirrai.hibiki.shared.profile.AppProfileBannerLayout
import org.akkirrai.hibiki.shared.profile.ProfileAvatarPlaceholder
import org.akkirrai.hibiki.shared.profile.ProfileAvatarImage
import org.akkirrai.hibiki.shared.profile.AppProfileFavoritesTab
import org.akkirrai.hibiki.shared.profile.ProfileLargePadding
import org.akkirrai.hibiki.shared.profile.ProfileSmallPadding
import org.akkirrai.hibiki.shared.profile.ProfileMediumPadding
import org.akkirrai.hibiki.shared.profile.ProfileNameEditor

private enum class LocalProfileTab(val titleRes: Int) {
    Overview(R.string.local_profile_tab_overview),
    Activity(R.string.local_profile_tab_activity),
    Favorites(R.string.local_profile_tab_favorites),
}

/**
 * Direct Android port of Animite's ProfileScreen layout: NestedScrollBannerLayout,
 * UserTabs, AboutTab's StatsRow, and its genre distribution arrangement.
 * The profile uses the local Hibiki profile snapshot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalProfileScreen(
    onSettingsClick: () -> Unit,
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
    viewModel: LocalProfileViewModel = viewModel(factory = LocalProfileViewModel.Factory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsState()
    var isEditingProfile by remember { mutableStateOf(false) }
    var editedName by remember(state.data.profileName) { mutableStateOf(state.data.profileName) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.updateProfileAvatar(it.toString()) }
    }
    val context = LocalContext.current
    val appLanguage = LocalAppLanguage.current
    val localizedResources = remember(context, appLanguage) {
        context.withLanguage(appLanguage).resources
    }
    val snapshot = remember(localizedResources, state.data) {
        buildProfileSnapshot(localizedResources, state.data)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        if (state.isLoading) {
            CircularProgressIndicator()
            return@Box
        }

        AppProfileBannerLayout(
            banner = { ratio, bannerModifier ->
                Box(modifier = bannerModifier) {
                    LocalAvatar(
                        ratio = ratio,
                        avatarUri = state.data.profileAvatarUri,
                        isEditing = isEditingProfile,
                        onEditClick = { avatarPicker.launch(arrayOf("image/*")) },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            },
            bannerElevatedContent = { _, actionModifier ->
                Row(
                    modifier = actionModifier,
                    horizontalArrangement = Arrangement.spacedBy(ProfileSmallPadding),
                ) {
                    ProfileEditActionButton(
                        isEditing = isEditingProfile,
                        contentDescription = stringResource(if (isEditingProfile) R.string.action_save else R.string.local_profile_edit),
                        onClick = {
                            if (isEditingProfile) {
                                viewModel.updateProfileName(editedName)
                            } else {
                                editedName = state.data.profileName
                            }
                            isEditingProfile = !isEditingProfile
                        },
                    )
                    ProfileSettingsActionButton(
                        contentDescription = stringResource(R.string.local_profile_settings),
                        onClick = onSettingsClick,
                    )
                }
            },
            contentBackgroundColor = MaterialTheme.colorScheme.surfaceContainer,
            bannerBackgroundColor = MaterialTheme.colorScheme.surfaceContainer,
            contentPadding = PaddingValues(top = ProfileLargePadding / 2),
            content = {
                org.akkirrai.hibiki.shared.profile.AppProfileIdentityTabs(
                    profileName = state.data.profileName,
                    isEditing = isEditingProfile,
                    horizontalPadding = ProfileLargePadding,
                    tabTitles = LocalProfileTab.entries.map { tab -> stringResource(tab.titleRes) },
                    nameEditorContent = {
                        ProfileNameEditor(
                            label = stringResource(R.string.local_profile_name),
                            name = editedName,
                            onNameChange = { editedName = it },
                        )
                    },
                    pageContent = { page ->
                        when (LocalProfileTab.entries[page]) {
                            LocalProfileTab.Overview -> org.akkirrai.hibiki.shared.profile.ProfileScrollableTab(
                                bottomContentPadding = bottomContentPadding,
                                verticalSpacing = ProfileMediumPadding,
                            ) {
                                org.akkirrai.hibiki.shared.profile.ProfileStatsRow(
                                    items = listOf(
                                        org.akkirrai.hibiki.shared.profile.ProfileStatItem(
                                            stringResource(R.string.local_profile_stat_total),
                                            snapshot.libraryTotal.toString(),
                                        ),
                                        org.akkirrai.hibiki.shared.profile.ProfileStatItem(
                                            stringResource(R.string.local_profile_stat_days),
                                            snapshot.activeDaysCount.toString(),
                                        ),
                                        org.akkirrai.hibiki.shared.profile.ProfileStatItem(
                                            stringResource(R.string.local_profile_stat_time),
                                            snapshot.watchTimeLabel,
                                        ),
                                    ),
                                )
                                org.akkirrai.hibiki.shared.profile.ProfileGenreBars(
                                    items = snapshot.genreSegments.map { item ->
                                        org.akkirrai.hibiki.shared.profile.ProfileGenreBarItem(
                                            item.label,
                                            item.count,
                                            item.color,
                                        )
                                    },
                                )
                                RecentLibraryCard(snapshot.recentLibraryItems)
                            }
                            LocalProfileTab.Activity -> org.akkirrai.hibiki.shared.profile.ProfileScrollableTab(
                                bottomContentPadding = bottomContentPadding,
                            ) {
                                AnalyticsCard(snapshot)
                            }
                            LocalProfileTab.Favorites -> AppProfileFavoritesTab(
                                isEmpty = snapshot.favoriteLibraryItems.isEmpty(),
                                bottomContentPadding = bottomContentPadding,
                                emptyContent = {
                                    Text(
                                        stringResource(R.string.local_profile_empty_favorites),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                            ) {
                                RecentLibraryCard(
                                    items = snapshot.favoriteLibraryItems,
                                    showTitle = false,
                                )
                            }
                        }
                    },
                )
            },
        )
    }
}

@Composable
private fun LocalAvatar(
    ratio: Float,
    avatarUri: String?,
    isEditing: Boolean,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    org.akkirrai.hibiki.shared.profile.ProfileAvatar(
        ratio = ratio,
        isEditing = isEditing,
        editContentDescription = stringResource(R.string.local_profile_change_avatar),
        onEditClick = onEditClick,
        modifier = modifier,
        avatarContent = { avatarModifier ->
            if (avatarUri.isNullOrBlank()) {
                ProfileAvatarPlaceholder(modifier = avatarModifier)
            } else {
                ProfileAvatarImage(url = avatarUri)
            }
        },
    )
}

private val AnimiteBannerHeight = 168.dp

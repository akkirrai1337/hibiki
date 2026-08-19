package org.akkirrai.hibiki.app.destination.profile

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.profile.LocalProfileData
import org.akkirrai.hibiki.profile.ProfileDestinationContent
import org.akkirrai.hibiki.app.settings.LanguageMode

internal data class AppDestinationProfileActions(
    val onNameChange: (String) -> Unit,
    val onEditClick: () -> Unit,
    val onSaveClick: () -> Unit,
    val onAvatarEdit: (((String) -> Unit) -> Unit),
    val onAvatarPicked: (String) -> Unit,
)

internal data class AppDestinationProfileState(
    val data: LocalProfileData,
    val isEditing: Boolean,
    val editedName: String,
    val isLoading: Boolean,
    val avatarEditAvailable: Boolean,
)

@Composable
internal fun ProfileDestinationRoute(
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
) {
    ProfileDestinationContent(
        profileData = profileData,
        profileLoading = profileLoading,
        profileAvatarEditAvailable = profileAvatarEditAvailable,
        isEditingProfile = isEditingProfile,
        editedProfileName = editedProfileName,
        languageMode = languageMode,
        systemLanguage = systemLanguage,
        bottomContentPadding = bottomContentPadding,
        onProfileNameChange = onProfileNameChange,
        onProfileEditClick = onProfileEditClick,
        onProfileSaveClick = onProfileSaveClick,
        onProfileSettingsClick = onProfileSettingsClick,
        onProfileAvatarEdit = onProfileAvatarEdit,
        onProfileAvatarPicked = onProfileAvatarPicked,
        modifier = Modifier.fillMaxSize(),
    )
}

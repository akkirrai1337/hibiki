package org.akkirrai.hibiki.shared.app.destination.routes

import org.akkirrai.hibiki.shared.app.destination.*

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.shared.profile.LocalProfileData
import org.akkirrai.hibiki.shared.profile.ProfileDestinationContent
import org.akkirrai.hibiki.shared.settings.LanguageMode

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

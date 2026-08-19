package org.akkirrai.hibiki.app.destination.profile

import org.akkirrai.hibiki.profile.LocalProfileData

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

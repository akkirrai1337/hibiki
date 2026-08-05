package org.akkirrai.hibiki.shared.app.destination.state

import org.akkirrai.hibiki.shared.profile.LocalProfileData

internal data class AppDestinationProfileState(
    val data: LocalProfileData,
    val isEditing: Boolean,
    val editedName: String,
    val isLoading: Boolean,
    val avatarEditAvailable: Boolean,
)

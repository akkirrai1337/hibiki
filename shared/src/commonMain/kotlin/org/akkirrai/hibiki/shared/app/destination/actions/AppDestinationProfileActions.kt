package org.akkirrai.hibiki.shared.app.destination.actions

internal data class AppDestinationProfileActions(
    val onNameChange: (String) -> Unit,
    val onEditClick: () -> Unit,
    val onSaveClick: () -> Unit,
    val onAvatarEdit: (((String) -> Unit) -> Unit),
    val onAvatarPicked: (String) -> Unit,
)

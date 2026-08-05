package org.akkirrai.hibiki.shared.app.shell.profile

import org.akkirrai.hibiki.shared.app.shell.runtime.DEFAULT_PROFILE_NAME

import org.akkirrai.hibiki.shared.profile.LocalProfileDataRepository
import org.akkirrai.hibiki.shared.profile.LocalProfilePresenter

internal class HibikiProfileActions(
    private val repository: LocalProfileDataRepository,
    private val presenter: LocalProfilePresenter,
    private val setEditing: (Boolean) -> Unit,
    private val getEditedName: () -> String,
    private val setEditedName: (String) -> Unit,
) {
    val onNameChange: (String) -> Unit = setEditedName

    val onEditClick: () -> Unit = {
        if (getEditedName().isBlank()) setEditedName(DEFAULT_PROFILE_NAME)
        setEditing(true)
    }

    val onSaveClick: () -> Unit = {
        val profileName = getEditedName().trim().ifBlank { DEFAULT_PROFILE_NAME }
        repository.updateProfileName(profileName)
        presenter.updateProfileName(profileName)
        setEditedName(profileName)
        setEditing(false)
    }

    val onAvatarPicked: (String) -> Unit = { uri ->
        repository.updateProfileAvatar(uri)
        presenter.updateProfileAvatar(uri)
    }
}

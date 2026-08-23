package org.akkirrai.hibiki.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.akkirrai.hibiki.app.shell.runtime.DEFAULT_PROFILE_NAME
import org.akkirrai.hibiki.profile.LocalProfileDataRepository
import org.akkirrai.hibiki.profile.LocalProfilePresenter

internal class ProfileEditingActions(
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

internal class ProfileEditingState(initialProfileName: String) {
    var isEditing by mutableStateOf(false)
    var editedName by mutableStateOf(initialProfileName)
}

@Composable
internal fun rememberProfileEditingState(profileName: String): ProfileEditingState {
    val state = remember {
        ProfileEditingState(profileName.ifBlank { DEFAULT_PROFILE_NAME })
    }
    LaunchedEffect(profileName) {
        state.editedName = profileName.ifBlank { DEFAULT_PROFILE_NAME }
    }
    return state
}

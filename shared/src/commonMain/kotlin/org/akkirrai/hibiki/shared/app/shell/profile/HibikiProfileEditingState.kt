package org.akkirrai.hibiki.shared.app.shell.profile

import org.akkirrai.hibiki.shared.app.shell.runtime.DEFAULT_PROFILE_NAME

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal class HibikiProfileEditingState(initialProfileName: String) {
    var isEditing by mutableStateOf(false)
    var editedName by mutableStateOf(initialProfileName)
}

@Composable
internal fun rememberHibikiProfileEditingState(profileName: String): HibikiProfileEditingState {
    val state = remember {
        HibikiProfileEditingState(profileName.ifBlank { DEFAULT_PROFILE_NAME })
    }
    LaunchedEffect(profileName) {
        state.editedName = profileName.ifBlank { DEFAULT_PROFILE_NAME }
    }
    return state
}

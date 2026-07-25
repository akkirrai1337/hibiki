package org.akkirrai.hibiki.shared.profile

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LocalProfileUiState(
    val data: LocalProfileData = LocalProfileData(),
    val isLoading: Boolean = false,
)

/** Lifecycle-neutral state holder for local profile hosts. */
class LocalProfilePresenter(
    initialState: LocalProfileUiState = LocalProfileUiState(),
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<LocalProfileUiState> = _state.asStateFlow()

    suspend fun load(repository: LocalProfileDataRepository) {
        _state.update { it.copy(isLoading = true) }
        try {
            setData(repository.load())
        } finally {
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun setData(data: LocalProfileData) {
        _state.value = LocalProfileUiState(data = data, isLoading = false)
    }

    fun updateProfileName(name: String) {
        _state.update { it.copy(data = it.data.copy(profileName = name)) }
    }

    fun updateProfileAvatar(uri: String) {
        _state.update { it.copy(data = it.data.copy(profileAvatarUri = uri)) }
    }
}

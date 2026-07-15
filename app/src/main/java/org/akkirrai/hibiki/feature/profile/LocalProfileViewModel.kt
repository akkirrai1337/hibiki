package org.akkirrai.hibiki.feature.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.core.profile.LocalProfileData
import org.akkirrai.hibiki.core.profile.LocalProfileRepository

class LocalProfileViewModel(
    private val repository: LocalProfileRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LocalProfileUiState(isLoading = true))
    val uiState: StateFlow<LocalProfileUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val data = repository.getData()
            _uiState.update { LocalProfileUiState(data = data, isLoading = false) }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LocalProfileViewModel(
            context.applicationContext.hibikiDependencies().localProfileRepository(),
        ) as T
    }
}

data class LocalProfileUiState(
    val data: LocalProfileData = LocalProfileData(),
    val isLoading: Boolean = false,
)

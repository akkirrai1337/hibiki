package org.akkirrai.hibiki.feature.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.core.profile.LocalProfileRepository
import org.akkirrai.hibiki.shared.profile.LocalProfilePresenter
import org.akkirrai.hibiki.shared.profile.LocalProfileUiState

class LocalProfileViewModel(
    private val repository: LocalProfileRepository,
) : ViewModel() {
    private val presenter = LocalProfilePresenter(LocalProfileUiState(isLoading = true))
    val uiState: StateFlow<LocalProfileUiState> = presenter.state

    init { refresh() }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            presenter.load(repository)
        }
    }

    fun updateProfileName(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val profileName = repository.updateProfileName(name)
            presenter.updateProfileName(profileName)
        }
    }

    fun updateProfileAvatar(uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateProfileAvatar(uri)
            presenter.updateProfileAvatar(uri)
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LocalProfileViewModel(
            context.applicationContext.hibikiDependencies().localProfileRepository(),
        ) as T
    }
}

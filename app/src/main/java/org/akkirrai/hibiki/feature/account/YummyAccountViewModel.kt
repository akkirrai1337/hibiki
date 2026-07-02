package org.akkirrai.hibiki.feature.account

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.akkirrai.animeresolver.metadata.YummyProfile
import org.akkirrai.animeresolver.metadata.YummyUserAnimeListItem
import org.akkirrai.animeresolver.metadata.YummyUserListWatchStat
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.account.YummyAccountRepository
import org.akkirrai.hibiki.core.account.YummyAccountSessionState

class YummyAccountViewModel(
    context: Context,
    private val repository: YummyAccountRepository = YummyAccountRepository(context.applicationContext),
) : ViewModel() {
    private val appContext = context.applicationContext
    private val _uiState = MutableStateFlow(
        YummyAccountUiState(
            apiKeyEnabled = repository.isApplicationTokenEnabled(),
            apiKeyAvailable = !repository.getApplicationToken().isNullOrBlank(),
        )
    )
    val uiState: StateFlow<YummyAccountUiState> = _uiState.asStateFlow()

    init {
        loadSession()
    }

    fun submitCredentials(login: String, secret: String) {
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val nextState = runCatching {
                val profile = repository.signIn(login = login, secret = secret)
                loadSignedInState(
                    repository = repository,
                    profile = profile,
                    refreshDetailedProfile = false,
                )
            }.fold(
                onSuccess = { it },
                onFailure = { throwable ->
                    YummyAccountScreenState.Error(
                        throwable.message ?: appContext.getString(R.string.yummy_account_login_error),
                    )
                },
            )
            _uiState.update {
                it.copy(
                    screenState = nextState,
                    busy = false,
                    apiKeyAvailable = !repository.getApplicationToken().isNullOrBlank(),
                )
            }
        }
    }

    fun setPage(page: AccountPage) {
        _uiState.update { it.copy(page = page) }
    }

    fun setApplicationTokenEnabled(enabled: Boolean) {
        repository.setApplicationTokenEnabled(enabled)
        _uiState.update { it.copy(apiKeyEnabled = enabled) }
    }

    fun signOut() {
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            repository.signOut()
            _uiState.update {
                it.copy(
                    screenState = YummyAccountScreenState.SignedOut,
                    busy = false,
                    page = AccountPage.Profile,
                )
            }
        }
    }

    private fun loadSession() {
        viewModelScope.launch(Dispatchers.IO) {
            val cachedProfile = repository.getCachedProfile().takeIf { repository.isLoggedIn() }
            if (cachedProfile != null) {
                _uiState.update {
                    it.copy(
                        screenState = YummyAccountScreenState.SignedIn(
                            profile = cachedProfile,
                            libraryItems = emptyList(),
                            listWatchStats = emptyList(),
                        )
                    )
                }
            }

            val nextState = when (val result = repository.validateSession()) {
                YummyAccountSessionState.LoggedOut -> YummyAccountScreenState.SignedOut
                is YummyAccountSessionState.LoggedIn -> loadSignedInState(
                    repository = repository,
                    profile = result.profile,
                    refreshDetailedProfile = false,
                )
                is YummyAccountSessionState.Invalid -> YummyAccountScreenState.SignedOut
            }
            _uiState.update { it.copy(screenState = nextState) }
        }
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }

    class Factory(
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return YummyAccountViewModel(context = context.applicationContext) as T
        }
    }
}

data class YummyAccountUiState(
    val screenState: YummyAccountScreenState = YummyAccountScreenState.Checking,
    val busy: Boolean = false,
    val apiKeyEnabled: Boolean = false,
    val apiKeyAvailable: Boolean = false,
    val page: AccountPage = AccountPage.Profile,
)

enum class AccountPage {
    Profile,
    Settings,
}

sealed interface YummyAccountScreenState {
    data object Checking : YummyAccountScreenState
    data object SignedOut : YummyAccountScreenState
    data class SignedIn(
        val profile: YummyProfile,
        val libraryItems: List<YummyUserAnimeListItem>,
        val listWatchStats: List<YummyUserListWatchStat>,
    ) : YummyAccountScreenState

    data class Error(val message: String) : YummyAccountScreenState
}

private suspend fun loadSignedInState(
    repository: YummyAccountRepository,
    profile: YummyProfile,
    refreshDetailedProfile: Boolean = true,
): YummyAccountScreenState.SignedIn {
    val (detailedProfile, libraryItems, listWatchStats) = coroutineScope {
        val detailedProfile = async {
            if (refreshDetailedProfile) {
                runCatching { repository.getUserProfile(profile.id) }
                    .getOrDefault(profile)
            } else {
                profile
            }
        }
        val libraryItems = async {
            runCatching { repository.getUserLists(profile.id) }
                .getOrDefault(emptyList())
        }
        val listWatchStats = async {
            runCatching { repository.getUserStatsLists(profile.id) }
                .getOrDefault(emptyList())
        }

        Triple(detailedProfile.await(), libraryItems.await(), listWatchStats.await())
    }
    return YummyAccountScreenState.SignedIn(detailedProfile, libraryItems, listWatchStats)
}

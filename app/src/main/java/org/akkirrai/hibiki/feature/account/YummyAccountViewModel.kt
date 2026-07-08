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
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.core.account.YummyAccountRepository
import org.akkirrai.hibiki.core.account.YummyAccountSessionState
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.network.NoInternetConnectionException
import org.akkirrai.hibiki.core.source.AnimeSearchRepository
import org.akkirrai.hibiki.core.source.OfflineTitleMetadataRepository

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
                    context = appContext,
                    repository = repository,
                    profile = profile,
                    refreshDetailedProfile = false,
                )
            }.fold(
                onSuccess = { it },
                onFailure = { throwable ->
                    AppLogger.w(
                        LOG_TAG,
                        "Sign-in failed: ${throwable::class.simpleName}: ${throwable.message}",
                        throwable,
                    )
                    YummyAccountScreenState.Error(
                        throwable.toSignInUiMessage(),
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

    fun setApplicationTokenEnabled(enabled: Boolean) {
        repository.setApplicationTokenEnabled(enabled)
        _uiState.update { it.copy(apiKeyEnabled = enabled) }
    }

    fun clearSignInError() {
        _uiState.update { state ->
            if (state.screenState is YummyAccountScreenState.Error) {
                state.copy(screenState = YummyAccountScreenState.SignedOut)
            } else {
                state
            }
        }
    }

    fun signOut() {
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            repository.signOut()
            _uiState.update {
                it.copy(
                    screenState = YummyAccountScreenState.SignedOut,
                    busy = false,
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
                            libraryMetadata = emptyList(),
                        )
                    )
                }
            }

            val nextState = when (val result = repository.validateSession()) {
                YummyAccountSessionState.LoggedOut -> YummyAccountScreenState.SignedOut
                is YummyAccountSessionState.LoggedIn -> loadSignedInState(
                    context = appContext,
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
            val dependencies = context.applicationContext.hibikiDependencies()
            return YummyAccountViewModel(
                context = context.applicationContext,
                repository = dependencies.accountRepository(),
            ) as T
        }
    }

    private fun Throwable.toSignInUiMessage(): String {
        val normalizedMessage = message.orEmpty().lowercase()

        return when {
            this is NoInternetConnectionException ->
                appContext.getString(R.string.home_error_no_internet)
            normalizedMessage.contains("error_code\":7") ||
                normalizedMessage.contains("invalid password") ||
                normalizedMessage.contains("wrong password") ||
                normalizedMessage.contains("invalid login") ->
                appContext.getString(R.string.yummy_account_error_invalid_credentials)
            normalizedMessage.contains("captcha") ->
                appContext.getString(R.string.yummy_account_error_captcha_required)
            normalizedMessage.contains("timeout") ||
                normalizedMessage.contains("timed out") ||
                normalizedMessage.contains("http 5") ||
                normalizedMessage.contains("server") ->
                appContext.getString(R.string.yummy_account_error_temporarily_unavailable)
            else -> appContext.getString(R.string.yummy_account_login_error)
        }
    }

    private companion object {
        const val LOG_TAG = "YummyAccount"
    }
}

data class YummyAccountUiState(
    val screenState: YummyAccountScreenState = YummyAccountScreenState.Checking,
    val busy: Boolean = false,
    val apiKeyEnabled: Boolean = false,
    val apiKeyAvailable: Boolean = false,
)

sealed interface YummyAccountScreenState {
    data object Checking : YummyAccountScreenState
    data object SignedOut : YummyAccountScreenState
    data class SignedIn(
        val profile: YummyProfile,
        val libraryItems: List<YummyUserAnimeListItem>,
        val listWatchStats: List<YummyUserListWatchStat>,
        val libraryMetadata: List<Anime>,
    ) : YummyAccountScreenState

    data class Error(val message: String) : YummyAccountScreenState
}

private suspend fun loadSignedInState(
    context: Context,
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
    val libraryMetadata = loadLibraryMetadata(context = context, libraryItems = libraryItems)
    return YummyAccountScreenState.SignedIn(detailedProfile, libraryItems, listWatchStats, libraryMetadata)
}

private suspend fun loadLibraryMetadata(
    context: Context,
    libraryItems: List<YummyUserAnimeListItem>,
): List<Anime> {
    val metadataRepository = OfflineTitleMetadataRepository(context)
    val searchRepository = AnimeSearchRepository(context)
    return try {
        libraryItems.mapNotNull { item ->
            val id = item.animeId.toString()
            metadataRepository.get(id)
                ?: runCatching {
                    val fallback = Anime(
                        id = id,
                        title = item.title,
                        subtitle = "",
                        episodesLabel = "",
                        status = "",
                        posterUrl = item.posterUrl,
                    )
                    searchRepository.getDetails(id = id, fallback = fallback)
                        .also(metadataRepository::save)
                }.getOrNull()
        }
    } finally {
        searchRepository.close()
    }
}

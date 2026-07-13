package org.akkirrai.hibiki.feature.account

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
import org.akkirrai.hibiki.core.profile.LocalProfileData
import org.akkirrai.hibiki.core.profile.LocalProfileRepository
import org.akkirrai.hibiki.core.source.AnimeSearchRepository
import org.akkirrai.hibiki.core.source.OfflineTitleMetadataRepository

class YummyAccountViewModel(
    context: Context,
    private val repository: YummyAccountRepository = YummyAccountRepository(context.applicationContext),
    private val localProfileRepository: LocalProfileRepository = LocalProfileRepository(context.applicationContext),
) : ViewModel() {
    private val appContext = context.applicationContext
    private val _uiState = MutableStateFlow(
        YummyAccountUiState()
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
                    localProfileRepository = localProfileRepository,
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
                )
            }
        }
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

    fun refreshLocalProfileData() {
        viewModelScope.launch(Dispatchers.IO) {
            val localData = localProfileRepository.getData()
            _uiState.update { state ->
                val signedIn = state.screenState as? YummyAccountScreenState.SignedIn ?: return@update state
                state.copy(screenState = signedIn.copy(localProfileData = localData))
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
                localProfileRepository.mergeRemoteActivity(cachedProfile.activityCountsByDay())
                _uiState.update {
                    it.copy(
                        screenState = YummyAccountScreenState.SignedIn(
                            profile = cachedProfile,
                            libraryItems = emptyList(),
                            listWatchStats = emptyList(),
                            libraryMetadata = emptyList(),
                            localProfileData = localProfileRepository.getData(),
                        )
                    )
                }
            }

            val nextState = when (val result = repository.validateSession()) {
                YummyAccountSessionState.LoggedOut -> YummyAccountScreenState.SignedOut
                is YummyAccountSessionState.LoggedIn -> loadSignedInState(
                    context = appContext,
                    repository = repository,
                    localProfileRepository = localProfileRepository,
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
                localProfileRepository = dependencies.localProfileRepository(),
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
)

sealed interface YummyAccountScreenState {
    data object Checking : YummyAccountScreenState
    data object SignedOut : YummyAccountScreenState
    data class SignedIn(
        val profile: YummyProfile,
        val libraryItems: List<YummyUserAnimeListItem>,
        val listWatchStats: List<YummyUserListWatchStat>,
        val libraryMetadata: List<Anime>,
        val localProfileData: LocalProfileData,
    ) : YummyAccountScreenState

    data class Error(val message: String) : YummyAccountScreenState
}

private suspend fun loadSignedInState(
    context: Context,
    repository: YummyAccountRepository,
    localProfileRepository: LocalProfileRepository,
    profile: YummyProfile,
    refreshDetailedProfile: Boolean = true,
): YummyAccountScreenState.SignedIn {
    val (detailedProfile, libraryItems, listWatchStats) = coroutineScope {
        val detailed = async { if (refreshDetailedProfile) runCatching { repository.getUserProfile(profile.id) }.getOrDefault(profile) else profile }
        val library = async { runCatching { repository.getUserLists(profile.id) }.getOrDefault(emptyList()) }
        val stats = async { runCatching { repository.getUserStatsLists(profile.id) }.getOrDefault(emptyList()) }
        Triple(detailed.await(), library.await(), stats.await())
    }
    val libraryMetadata = loadLibraryMetadata(context, libraryItems)
    localProfileRepository.mergeRemoteActivity(detailedProfile.activityCountsByDay())
    return YummyAccountScreenState.SignedIn(
        profile = detailedProfile,
        libraryItems = libraryItems,
        listWatchStats = listWatchStats,
        libraryMetadata = libraryMetadata,
        localProfileData = localProfileRepository.getData(),
    )
}

private fun YummyProfile.activityCountsByDay(): Map<LocalDate, Int> = watches?.history.orEmpty()
    .filter { (it.duration ?: 0L) > 0L || (it.episodeCount ?: 0) > 0 }
    .mapNotNull { item ->
        item.date?.let { value ->
            val instant = if (value >= 1_000_000_000_000L) Instant.ofEpochMilli(value) else Instant.ofEpochSecond(value)
            instant.atZone(ZoneId.systemDefault()).toLocalDate() to (item.episodeCount ?: 1).coerceAtLeast(1)
        }
    }
    .groupBy({ it.first }, { it.second })
    .mapValues { (_, counts) -> counts.sum() }

private suspend fun loadLibraryMetadata(context: Context, libraryItems: List<YummyUserAnimeListItem>): List<Anime> {
    val metadataRepository = OfflineTitleMetadataRepository(context)
    val searchRepository = AnimeSearchRepository(context)
    return try {
        libraryItems.mapNotNull { item ->
            metadataRepository.get(item.animeId.toString()) ?: runCatching {
                Anime(id = item.animeId.toString(), title = item.title, subtitle = "", episodesLabel = "", status = "", posterUrl = item.posterUrl)
                    .let { fallback -> searchRepository.getDetails(id = fallback.id, fallback = fallback).also(metadataRepository::save) }
            }.getOrNull()
        }
    } finally { searchRepository.close() }
}

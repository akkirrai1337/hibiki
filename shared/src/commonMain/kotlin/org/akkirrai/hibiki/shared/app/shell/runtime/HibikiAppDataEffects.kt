package org.akkirrai.hibiki.shared.app.shell.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CancellationException
import org.akkirrai.hibiki.shared.home.data.HomeDataRepository
import org.akkirrai.hibiki.shared.home.presentation.HomePresenter
import org.akkirrai.hibiki.shared.home.state.HomeUiState
import org.akkirrai.hibiki.shared.library.presentation.LibraryPresenter
import org.akkirrai.hibiki.shared.library.LibraryRepository
import org.akkirrai.hibiki.shared.profile.LocalProfileData
import org.akkirrai.hibiki.shared.profile.LocalProfileDataRepository
import org.akkirrai.hibiki.shared.profile.LocalProfilePresenter

@Composable
internal fun HibikiAppDataEffects(
    libraryRepository: LibraryRepository,
    libraryPresenter: LibraryPresenter,
    selectedAnimeKey: String?,
    homeRepository: HomeDataRepository?,
    homePresenter: HomePresenter,
    setHomeState: (HomeUiState) -> Unit,
    profileRepository: LocalProfileDataRepository,
    profilePresenter: LocalProfilePresenter,
) {
    LaunchedEffect(libraryRepository, selectedAnimeKey) {
        try {
            libraryPresenter.updateEntries(libraryRepository.getEntries())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            libraryPresenter.updateEntries(emptyList())
        }
    }

    LaunchedEffect(homeRepository) {
        try {
            if (homeRepository == null) {
                homePresenter.setState(HomeUiState())
            } else {
                homePresenter.setState(homeRepository.fallbackHomeState())
                setHomeState(homeRepository.loadHomeState())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            homePresenter.setState(
                HomeUiState(
                    errorMessage = throwable.message ?: "Home loading failed",
                ),
            )
        }
    }

    LaunchedEffect(profileRepository) {
        try {
            profilePresenter.load(profileRepository)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            profilePresenter.setData(LocalProfileData())
        }
    }
}

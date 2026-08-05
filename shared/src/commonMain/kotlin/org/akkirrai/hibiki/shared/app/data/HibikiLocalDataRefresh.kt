package org.akkirrai.hibiki.shared.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.shared.home.data.HomeDataRepository
import org.akkirrai.hibiki.shared.library.presentation.LibraryPresenter
import org.akkirrai.hibiki.shared.library.LibraryRepository
import org.akkirrai.hibiki.shared.profile.LocalProfileDataRepository
import org.akkirrai.hibiki.shared.profile.LocalProfilePresenter

internal fun launchLocalDataRefresh(
    scope: CoroutineScope,
    libraryPresenter: LibraryPresenter,
    libraryRepository: LibraryRepository,
    profilePresenter: LocalProfilePresenter,
    profileRepository: LocalProfileDataRepository,
    homeRepository: HomeDataRepository?,
    setHomeState: (org.akkirrai.hibiki.shared.home.state.HomeUiState) -> Unit,
) {
    scope.launch {
        try {
            libraryPresenter.updateEntries(libraryRepository.getEntries())
            profilePresenter.load(profileRepository)
            homeRepository?.let { repository ->
                setHomeState(repository.loadHomeState())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            println("Hibiki local data refresh failed: ${throwable.message ?: throwable::class.simpleName}")
        }
    }
}

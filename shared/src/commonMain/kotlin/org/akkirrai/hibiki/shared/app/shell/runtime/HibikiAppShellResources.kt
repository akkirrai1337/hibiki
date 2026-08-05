package org.akkirrai.hibiki.shared.app.shell.runtime


import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.CoroutineScope
import org.akkirrai.hibiki.shared.catalog.presentation.AnimeCatalogPresenter
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogRepository
import org.akkirrai.hibiki.shared.catalog.presentation.SourcesSearchPresenter
import org.akkirrai.hibiki.shared.home.data.HomeDataRepository
import org.akkirrai.hibiki.shared.home.presentation.HomePresenter
import org.akkirrai.hibiki.shared.home.presentation.HomeSearchPresenter
import org.akkirrai.hibiki.shared.library.presentation.LibraryPresenter
import org.akkirrai.hibiki.shared.library.LibraryRepository
import org.akkirrai.hibiki.shared.player.EpisodesPresenter
import org.akkirrai.hibiki.shared.player.PlayerPresenter
import org.akkirrai.hibiki.shared.player.PlayerUiState
import org.akkirrai.hibiki.shared.player.WatchDataRepository
import org.akkirrai.hibiki.shared.player.WatchSourcesPresenter
import org.akkirrai.hibiki.shared.profile.LocalProfileDataRepository
import org.akkirrai.hibiki.shared.profile.LocalProfilePresenter
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor

internal class HibikiAppShellResources(
    val presenter: AnimeCatalogPresenter,
    val homeSearchPresenter: HomeSearchPresenter,
    val homePresenter: HomePresenter,
    val catalogListState: LazyListState,
    val settingsListState: LazyListState,
    val externalSourcesListState: LazyListState,
    val sourceSearchPresenter: SourcesSearchPresenter,
    val watchPresenter: WatchSourcesPresenter,
    val episodesPresenter: EpisodesPresenter,
    val playerPresenter: PlayerPresenter,
    val libraryPresenter: LibraryPresenter,
    val profilePresenter: LocalProfilePresenter,
    val homeDescriptionRequests: MutableSet<String>,
)

@Composable
internal fun rememberHibikiAppShellResources(
    repository: AnimeCatalogRepository,
    homeRepository: HomeDataRepository?,
    libraryRepository: LibraryRepository,
    profileRepository: LocalProfileDataRepository,
    watchRepository: WatchDataRepository?,
    sources: List<AppSourceDescriptor>,
    selectedSourceId: String?,
    scope: CoroutineScope,
): HibikiAppShellResources = HibikiAppShellResources(
    presenter = remember(repository) { AnimeCatalogPresenter(repository, scope, pageSize = HOME_SEARCH_PAGE_SIZE) },
    homeSearchPresenter = remember(repository) { HomeSearchPresenter(repository, scope, pageSize = HOME_SEARCH_PAGE_SIZE) },
    homePresenter = remember(homeRepository) { HomePresenter() },
    catalogListState = rememberSaveable(selectedSourceId, saver = LazyListState.Saver) { LazyListState() },
    settingsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() },
    externalSourcesListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() },
    sourceSearchPresenter = remember(repository, sources) { SourcesSearchPresenter(repository, sources, scope) },
    watchPresenter = remember(watchRepository) { WatchSourcesPresenter() },
    episodesPresenter = remember(watchRepository) { EpisodesPresenter() },
    playerPresenter = remember(watchRepository) { PlayerPresenter(PlayerUiState(isLoading = false)) },
    libraryPresenter = remember(libraryRepository) { LibraryPresenter() },
    profilePresenter = remember(profileRepository) { LocalProfilePresenter() },
    homeDescriptionRequests = remember(homeRepository) { mutableSetOf() },
)

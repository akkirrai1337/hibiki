package org.akkirrai.hibiki.shared.app.shell.navigation

import org.akkirrai.hibiki.shared.app.destination.actions.AppDestinationNavigationActions
import org.akkirrai.hibiki.shared.navigation.AppDestination
import org.akkirrai.hibiki.shared.navigation.AppNavigationState
import org.akkirrai.hibiki.shared.navigation.AppNavigationEvent
import org.akkirrai.hibiki.shared.navigation.navigateToSourceRepositories
import org.akkirrai.hibiki.shared.navigation.navigateToSourceRepository
import org.akkirrai.hibiki.shared.navigation.navigateToSourcePackageInfo
import org.akkirrai.hibiki.shared.navigation.navigateToSettings
import org.akkirrai.hibiki.shared.navigation.reduce
import org.akkirrai.hibiki.shared.app.shell.player.watch.HibikiWatchFlowNavigationActions

internal fun createHibikiAppShellDestinationNavigationActions(
    detailsNavigationActions: HibikiDetailsNavigationActions,
    watchFlowNavigationActions: HibikiWatchFlowNavigationActions,
    rootNavigationCoordinator: HibikiRootNavigationCoordinator,
    activeDownloadMode: Boolean,
    updateNavigationState: ((AppNavigationState) -> AppNavigationState) -> Unit,
    onSourceSelected: (String) -> Unit,
): AppDestinationNavigationActions = AppDestinationNavigationActions(
    onAnimeClick = detailsNavigationActions::open,
    onBackFromDetails = detailsNavigationActions::close,
    onWatchClick = { anime ->
        watchFlowNavigationActions.openWatch(anime, activeDownloadMode)
    },
    onBackFromWatch = watchFlowNavigationActions::backFromWatch,
    onProfileSettingsClick = {
        updateNavigationState { it.navigateToSettings() }
    },
    onSourceSelected = onSourceSelected,
    onBrowseCatalog = { rootNavigationCoordinator.select(AppDestination.CATALOG) },
    onOpenLibrary = { rootNavigationCoordinator.select(AppDestination.LIBRARY) },
    onSourceRepositoriesClick = {
        updateNavigationState { it.navigateToSourceRepositories() }
    },
    onSourceRepositoryClick = { url ->
        updateNavigationState { it.navigateToSourceRepository(url) }
    },
    onSourcePackageInfoClick = { repositoryUrl, sourceId ->
        updateNavigationState { it.navigateToSourcePackageInfo(repositoryUrl, sourceId) }
    },
    onSettingsBack = {
        updateNavigationState { it.reduce(AppNavigationEvent.Back) }
    },
)

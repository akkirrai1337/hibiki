package org.akkirrai.hibiki.shared.app.destination.context

import org.akkirrai.hibiki.shared.app.destination.actions.*
import org.akkirrai.hibiki.shared.app.destination.state.*
import org.akkirrai.hibiki.shared.navigation.AppDestination

/** Complete shared input contract for rendering one destination route. */
internal class AppDestinationContentInput(
    val selectedTab: AppDestination,
    val catalog: CatalogContentInput,
    val home: HomeContentInput,
    val library: LibraryContentInput,
    val settings: SettingsContentInput,
    val sources: SourcesContentInput,
    val profile: ProfileContentInput,
    val watch: WatchContentInput,
    val platform: PlatformContentInput,
    val navigation: NavigationContentInput,
) {
}

internal data class CatalogContentInput(
    val actions: AppDestinationCatalogActions,
    val state: AppDestinationCatalogState,
)

internal data class HomeContentInput(
    val state: AppDestinationHomeState,
    val actions: AppDestinationHomeActions,
)

internal data class LibraryContentInput(
    val state: AppDestinationLibraryState,
    val actions: AppDestinationLibraryActions,
)

internal data class SettingsContentInput(
    val actions: AppDestinationSettingsActions,
    val state: AppDestinationSettingsState,
    val listsState: AppDestinationSettingsListsState,
)

internal data class SourcesContentInput(
    val state: AppDestinationSourceState,
    val searchActions: AppDestinationSourceSearchActions,
    val externalSourcesState: AppDestinationExternalSourcesState,
)

internal data class ProfileContentInput(
    val state: AppDestinationProfileState,
    val actions: AppDestinationProfileActions,
)

internal data class WatchContentInput(
    val actions: AppDestinationWatchActions,
    val state: AppDestinationContentState,
    val playbackContext: AppDestinationPlaybackContext,
)

internal data class PlatformContentInput(
    val dataContext: AppDestinationDataContext,
    val hostContext: AppDestinationHostContext,
)

internal data class NavigationContentInput(
    val actions: AppDestinationNavigationActions,
    val detailsOverlayState: AppDestinationDetailsOverlayState,
)

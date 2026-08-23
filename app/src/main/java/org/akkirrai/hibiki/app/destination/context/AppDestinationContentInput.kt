package org.akkirrai.hibiki.app.destination.context

import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.app.destination.catalog.*
import org.akkirrai.hibiki.app.destination.home.*
import org.akkirrai.hibiki.app.destination.library.*
import org.akkirrai.hibiki.app.destination.profile.*
import org.akkirrai.hibiki.app.destination.settings.*
import org.akkirrai.hibiki.app.destination.source.*
import org.akkirrai.hibiki.app.destination.watch.*
import org.akkirrai.hibiki.app.shell.navigation.AppDestinationNavigationActions
import org.akkirrai.hibiki.library.LibraryRepository
import org.akkirrai.hibiki.app.navigation.AppDestination
import org.akkirrai.hibiki.profile.LocalProfileDataRepository
import org.akkirrai.hibiki.app.settings.LanguageMode
import org.akkirrai.hibiki.app.settings.SettingsScreenActions
import org.akkirrai.hibiki.app.settings.SettingsScreenState

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
    val actions: SettingsScreenActions,
    val state: SettingsScreenState,
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

internal data class AppDestinationDataContext(
    val libraryRepository: LibraryRepository,
    val profileRepository: LocalProfileDataRepository,
    val languageMode: LanguageMode,
)

internal data class AppDestinationHostContext(
    val systemLanguage: String,
    val includeNavigationBarPadding: Boolean,
    val onLibraryChanged: () -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onGitHubClick: () -> Unit,
    val modifier: Modifier,
)

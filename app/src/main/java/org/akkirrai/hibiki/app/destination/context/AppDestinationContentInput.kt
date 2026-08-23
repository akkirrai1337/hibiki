package org.akkirrai.hibiki.app.destination.context

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.app.destination.home.*
import org.akkirrai.hibiki.app.destination.library.*
import org.akkirrai.hibiki.app.destination.settings.*
import org.akkirrai.hibiki.app.destination.source.*
import org.akkirrai.hibiki.app.destination.watch.*
import org.akkirrai.hibiki.app.shell.navigation.AppDestinationNavigationActions
import org.akkirrai.hibiki.core.source.AppSourceConfigContent
import org.akkirrai.hibiki.library.LibraryRepository
import org.akkirrai.hibiki.app.navigation.AppDestination
import org.akkirrai.hibiki.profile.LocalProfileDataRepository
import org.akkirrai.hibiki.profile.LocalProfilePresenter
import org.akkirrai.hibiki.app.settings.LanguageMode
import org.akkirrai.hibiki.app.settings.SettingsScreenActions
import org.akkirrai.hibiki.app.settings.SettingsScreenState
import org.akkirrai.hibiki.catalog.presentation.AnimeCatalogPresenter
import org.akkirrai.hibiki.catalog.screen.CatalogActions
import org.akkirrai.hibiki.home.screen.HomeActions
import org.akkirrai.hibiki.library.screen.LibraryActions
import org.akkirrai.hibiki.library.state.LibrarySearchFilters

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
    val presenter: AnimeCatalogPresenter,
    val listState: LazyListState,
    val actions: CatalogActions,
)

internal data class HomeContentInput(
    val state: AppDestinationHomeState,
    val actions: HomeActions,
    val onRefresh: () -> Unit,
)

internal data class LibraryContentInput(
    val state: AppDestinationLibraryState,
    val actions: LibraryActions,
    val onFiltersApply: (LibrarySearchFilters) -> Unit,
)

internal data class SettingsContentInput(
    val actions: SettingsScreenActions,
    val state: SettingsScreenState,
    val listsState: AppDestinationSettingsListsState,
)

internal data class SourcesContentInput(
    val state: AppDestinationSourceState,
    val externalSourcesState: AppDestinationExternalSourcesState,
    val sourceConfigContent: AppSourceConfigContent?,
)

internal data class ProfileContentInput(
    val profilePresenter: LocalProfilePresenter,
    val avatarEditAvailable: Boolean,
    val onAvatarEdit: (((String) -> Unit) -> Unit),
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
    val modifier: Modifier,
)

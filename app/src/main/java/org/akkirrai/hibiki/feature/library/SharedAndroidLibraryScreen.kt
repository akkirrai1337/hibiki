package org.akkirrai.hibiki.feature.library

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.LocalAppLanguage
import org.akkirrai.hibiki.core.design.component.AnimeSourceBadge
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.source.labelResId
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.feature.home.AnimeSearchFiltersSheet
import org.akkirrai.hibiki.shared.library.AppLibraryScreen
import org.akkirrai.hibiki.shared.library.AppLibraryScreenLabels
import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.library.buildLibraryFilterCatalog
import org.akkirrai.hibiki.shared.library.isRussianLibraryLanguage
import org.akkirrai.hibiki.shared.library.toAnimeSearchFilters
import org.akkirrai.hibiki.shared.library.toLibrarySearchFilters
import org.akkirrai.hibiki.shared.settings.LanguageMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SharedAndroidLibraryScreen(
    onAnimeClick: (Anime) -> Unit,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = UiDimens.ScreenPadding,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsState()
    val languageMode = LocalAppLanguage.current

    LaunchedEffect(languageMode) { viewModel.onLanguageChanged() }
    LaunchedEffect(isActive) {
        if (isActive) {
            delay(LIBRARY_DEFERRED_SYNC_DELAY_MS)
            viewModel.syncFromStorage()
        }
    }

    AppLibraryScreen(
        state = state,
        labels = sharedAndroidLibraryLabels(),
        bottomContentPadding = bottomContentPadding,
        onAnimeClick = onAnimeClick,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onClearSearch = viewModel::clearSearch,
        onFilterClick = {},
        onCategorySelected = viewModel::selectCategory,
        entryContent = { entry, entryModifier ->
            org.akkirrai.hibiki.shared.library.AppLibraryEntryCard(
                entry = entry,
                announcementLabel = stringResource(R.string.anime_meta_announcement),
                movieLabel = stringResource(R.string.anime_meta_movie),
                onClick = { onAnimeClick(entry.anime) },
                libraryStatusLabel = { category -> stringResource(category.labelResId) },
                sourceBadgeContent = { titleId -> AnimeSourceBadge(titleId = titleId) },
                modifier = entryModifier,
            )
        },
        filterContent = { onDismiss ->
            SharedAndroidLibrarySearchFiltersSheet(
                catalog = state.filterCatalog,
                currentFilters = state.searchFilters,
                languageMode = languageMode,
                onDismiss = onDismiss,
                onApply = { filters ->
                    viewModel.applySearchFilters(filters)
                    onDismiss()
                },
            )
        },
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
private fun sharedAndroidLibraryLabels() = AppLibraryScreenLabels(
    searchPlaceholder = stringResource(R.string.search_placeholder),
    filterContentDescription = stringResource(R.string.search_filters),
    clearContentDescription = stringResource(R.string.home_search_clear),
    categoryLabels = LibraryCategory.entries.associateWith { stringResource(it.labelResId) },
    emptyTitle = stringResource(R.string.library_empty_title),
    emptyMessage = stringResource(R.string.library_empty_body),
    filteredTitle = stringResource(R.string.library_section_empty_title),
    searchTitle = stringResource(R.string.home_search_empty_title),
    filteredMessage = stringResource(R.string.home_search_empty_message),
    categoryEmptyLabels = mapOf(
        LibraryCategory.Watching to stringResource(R.string.library_empty_watching),
        LibraryCategory.Planned to stringResource(R.string.library_empty_planned),
        LibraryCategory.Completed to stringResource(R.string.library_empty_completed),
        LibraryCategory.Dropped to stringResource(R.string.library_empty_dropped),
        LibraryCategory.OnHold to stringResource(R.string.library_empty_on_hold),
        LibraryCategory.Favorite to stringResource(R.string.library_empty_favorite),
        LibraryCategory.Saved to stringResource(R.string.library_empty_saved),
    ),
    announcementLabel = stringResource(R.string.anime_meta_announcement),
    movieLabel = stringResource(R.string.anime_meta_movie),
    libraryStatusLabel = { category -> stringResource(category.labelResId) },
)

@Composable
private fun SharedAndroidLibrarySearchFiltersSheet(
    catalog: LibraryFilterCatalog,
    currentFilters: LibrarySearchFilters,
    languageMode: LanguageMode,
    onDismiss: () -> Unit,
    onApply: (LibrarySearchFilters) -> Unit,
) {
    val isRussian = isRussianLibraryLanguage(
        languageMode = languageMode,
        systemLanguage = LocalConfiguration.current.locales[0]?.language.orEmpty(),
    )
    val sharedCatalog = buildLibraryFilterCatalog(
        typeOptions = catalog.typeOptions,
        statusOptions = catalog.statusOptions,
        genreOptions = catalog.genreOptions,
        isRussian = isRussian,
    )
    AnimeSearchFiltersSheet(
        initialFilters = currentFilters.toAnimeSearchFilters(),
        filterCatalog = sharedCatalog,
        isFilterCatalogLoading = false,
        onApply = { filters -> onApply(filters.toLibrarySearchFilters(catalog)) },
        onDismissRequest = onDismiss,
        optionText = { it.title },
        maxCollapsedGenreGroups = 3,
        maxCollapsedGenreItems = null,
    )
}

private const val LIBRARY_DEFERRED_SYNC_DELAY_MS = 420L

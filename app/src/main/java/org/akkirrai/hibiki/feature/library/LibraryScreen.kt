package org.akkirrai.hibiki.feature.library

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.LocalAppLanguage
import org.akkirrai.hibiki.shared.library.icon
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.core.design.component.AnimeSourceBadge
import org.akkirrai.hibiki.shared.design.component.AppImagePlaceholder
import org.akkirrai.hibiki.shared.design.component.AppPosterImage
import org.akkirrai.hibiki.shared.library.LibraryStatusPosterFooter
import org.akkirrai.hibiki.core.log.PerfLogger
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.shared.model.buildCardMeta
import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.library.AppLibraryEmptyState
import org.akkirrai.hibiki.shared.library.AppLibraryEntryCard
import org.akkirrai.hibiki.shared.library.libraryStatusAlias
import org.akkirrai.hibiki.shared.library.resolveLibraryEmptyStateText
import org.akkirrai.hibiki.shared.library.buildLibraryFilterCatalog
import org.akkirrai.hibiki.shared.library.toAnimeSearchFilters
import org.akkirrai.hibiki.shared.library.toLibrarySearchFilters
import org.akkirrai.hibiki.shared.library.isRussianLibraryLanguage
import org.akkirrai.hibiki.shared.settings.LanguageMode
import org.akkirrai.hibiki.feature.home.AnimeSearchFiltersSheet
import org.akkirrai.hibiki.core.source.labelResId
import org.akkirrai.hibiki.core.source.LibraryEntry

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onAnimeClick: (Anime) -> Unit,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = UiDimens.ScreenPadding,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsState()
    val languageMode = LocalAppLanguage.current
    var isFilterDialogVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        PerfLogger.mark("LibraryScreen composed")
    }

    LaunchedEffect(languageMode) {
        viewModel.onLanguageChanged()
    }

    LaunchedEffect(isActive) {
        if (isActive) {
            PerfLogger.mark("LibraryScreen active", "defer=${LIBRARY_DEFERRED_SYNC_DELAY_MS}ms")
            delay(LIBRARY_DEFERRED_SYNC_DELAY_MS)
            PerfLogger.mark("LibraryScreen deferred sync trigger")
            viewModel.syncFromStorage()
        } else {
            PerfLogger.mark("LibraryScreen inactive")
        }
    }

    org.akkirrai.hibiki.shared.library.AppLibraryScreen(
        state = state,
        modifier = modifier.fillMaxSize(),
        bottomContentPadding = bottomContentPadding,
        onEntryClick = { entry -> onAnimeClick(entry.anime) },
        headerContent = {
            org.akkirrai.hibiki.shared.library.AppLibraryHeader(
                searchContent = { searchModifier ->
                    org.akkirrai.hibiki.shared.library.AppLibrarySearchBar(
                        query = state.searchQuery,
                        onQueryChange = viewModel::onSearchQueryChange,
                        onClear = viewModel::clearSearch,
                        placeholder = stringResource(R.string.search_placeholder),
                        filterContentDescription = stringResource(R.string.search_filters),
                        clearContentDescription = stringResource(R.string.home_search_clear),
                        onFilterClick = { isFilterDialogVisible = true },
                        modifier = searchModifier,
                    )
                },
                selected = state.selectedCategory,
                categories = state.orderedCategories,
                counts = state.categoryCounts,
                label = { stringResource(it.labelResId) },
                icon = { it.icon() },
                onSelected = viewModel::selectCategory,
            )
        },
        emptyContent = { filtered ->
            val emptyState = resolveLibraryEmptyStateText(
                filtered = filtered,
                searchQuery = state.searchQuery,
                category = state.selectedCategory,
                emptyTitle = stringResource(R.string.library_empty_title),
                emptyMessage = stringResource(R.string.library_empty_body),
                filteredTitle = stringResource(R.string.library_section_empty_title),
                searchTitle = stringResource(R.string.home_search_empty_title),
                filteredMessage = stringResource(R.string.home_search_empty_message),
                categoryLabels = mapOf(
                    LibraryCategory.Watching to stringResource(R.string.library_empty_watching),
                    LibraryCategory.Planned to stringResource(R.string.library_empty_planned),
                    LibraryCategory.Completed to stringResource(R.string.library_empty_completed),
                    LibraryCategory.Dropped to stringResource(R.string.library_empty_dropped),
                    LibraryCategory.OnHold to stringResource(R.string.library_empty_on_hold),
                    LibraryCategory.Favorite to stringResource(R.string.library_empty_favorite),
                    LibraryCategory.Saved to stringResource(R.string.library_empty_saved),
                ),
            )
            AppLibraryEmptyState(
                title = emptyState.title,
                message = emptyState.message,
            )
        },
        entryContent = { entry, entryModifier ->
            AppLibraryEntryCard(
                entry = entry,
                announcementLabel = stringResource(R.string.anime_meta_announcement),
                movieLabel = stringResource(R.string.anime_meta_movie),
                onClick = { onAnimeClick(entry.anime) },
                libraryStatusLabel = { category -> stringResource(category.labelResId) },
                sourceBadgeContent = { titleId -> AnimeSourceBadge(titleId = titleId) },
                modifier = entryModifier,
            )
        },
        filterContent = if (isFilterDialogVisible) {
            {
                LibrarySearchFiltersSheet(
                    catalog = state.filterCatalog,
                    currentFilters = state.searchFilters,
                    languageMode = languageMode,
                    onDismiss = { isFilterDialogVisible = false },
                    onApply = { filters ->
                        viewModel.applySearchFilters(filters)
                        isFilterDialogVisible = false
                    },
                )
            }
        } else null,
    )
}

private const val LIBRARY_DEFERRED_SYNC_DELAY_MS = 420L

@Composable
private fun LibrarySearchFiltersSheet(
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
    val sharedFilters = currentFilters.toAnimeSearchFilters()
    AnimeSearchFiltersSheet(
        initialFilters = sharedFilters,
        filterCatalog = sharedCatalog,
        isFilterCatalogLoading = false,
        onApply = { filters -> onApply(filters.toLibrarySearchFilters(catalog)) },
        onDismissRequest = onDismiss,
        optionText = { it.title },
        maxCollapsedGenreGroups = 3,
        maxCollapsedGenreItems = null,
    )
}

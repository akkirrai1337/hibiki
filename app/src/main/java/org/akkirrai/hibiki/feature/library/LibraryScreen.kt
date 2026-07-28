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
import org.akkirrai.hibiki.shared.library.libraryStatusLabel
import org.akkirrai.hibiki.shared.library.resolveLibraryEmptyStateMessage
import org.akkirrai.hibiki.shared.settings.LanguageMode
import org.akkirrai.hibiki.feature.home.AnimeSearchFiltersSheet
import org.akkirrai.hibiki.shared.model.AnimeCatalogCapabilities
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilter
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilterOption
import org.akkirrai.hibiki.shared.model.AnimeSearchFilters
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

    org.akkirrai.hibiki.shared.library.AppLibraryEntriesContent(
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
            if (!filtered) {
                AppLibraryEmptyState(
                    title = stringResource(R.string.library_empty_title),
                    message = stringResource(R.string.library_empty_body),
                )
            } else {
                AppLibraryEmptyState(
                    title = if (state.searchQuery.isBlank()) {
                        stringResource(R.string.library_section_empty_title)
                    } else {
                        stringResource(R.string.home_search_empty_title)
                    },
                    message = if (state.searchQuery.isBlank()) {
                resolveLibraryEmptyStateMessage(
                    category = state.selectedCategory,
                    labels = mapOf(
                        LibraryCategory.Watching to stringResource(R.string.library_empty_watching),
                        LibraryCategory.Planned to stringResource(R.string.library_empty_planned),
                        LibraryCategory.Completed to stringResource(R.string.library_empty_completed),
                        LibraryCategory.Dropped to stringResource(R.string.library_empty_dropped),
                        LibraryCategory.OnHold to stringResource(R.string.library_empty_on_hold),
                        LibraryCategory.Favorite to stringResource(R.string.library_empty_favorite),
                        LibraryCategory.Saved to stringResource(R.string.library_empty_saved),
                    ),
                )
                    } else {
                        stringResource(R.string.home_search_empty_message)
                    },
                )
            }
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
    )

    if (isFilterDialogVisible) {
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
    val isRussian = when (languageMode) {
        LanguageMode.RUSSIAN -> true
        LanguageMode.ENGLISH -> false
        LanguageMode.SYSTEM -> LocalConfiguration.current.locales[0]?.language == "ru"
    }
    val sharedCatalog = catalog.toSharedFilterCatalog(isRussian)
    val sharedFilters = currentFilters.toSharedFilters()
    AnimeSearchFiltersSheet(
        initialFilters = sharedFilters,
        filterCatalog = sharedCatalog,
        isFilterCatalogLoading = false,
        onApply = { filters -> onApply(filters.toLibraryFilters(catalog)) },
        onDismissRequest = onDismiss,
        optionText = { it.title },
        maxCollapsedGenreGroups = 3,
        maxCollapsedGenreItems = null,
    )
}

private fun LibraryFilterCatalog.toSharedFilterCatalog(isRussian: Boolean): AnimeCatalogFilterCatalog {
    val statuses = statusOptions.map { status ->
        AnimeCatalogFilterOption(
            id = libraryStatusAlias(status),
            title = libraryStatusLabel(status, isRussian),
        )
    }.distinctBy(AnimeCatalogFilterOption::id)
    return AnimeCatalogFilterCatalog(
        typeOptions = typeOptions.map { AnimeCatalogFilterOption(it.lowercase(), it.uppercase()) },
        statusOptions = statuses,
        genreOptions = genreOptions.map { AnimeCatalogFilterOption(it, it) },
        capabilities = AnimeCatalogCapabilities(
            supportedFilters = setOf(
                AnimeCatalogFilter.TYPE,
                AnimeCatalogFilter.STATUS,
                AnimeCatalogFilter.INCLUDED_GENRES,
                AnimeCatalogFilter.EXCLUDED_GENRES,
                AnimeCatalogFilter.YEAR_RANGE,
            ),
        ),
    )
}

private fun LibrarySearchFilters.toSharedFilters(): AnimeSearchFilters = AnimeSearchFilters(
    typeAlias = type?.lowercase(),
    statusAlias = status?.let(::libraryStatusAlias),
    includedGenreAliases = includedGenres,
    excludedGenreAliases = excludedGenres,
    yearFrom = yearFrom,
    yearTo = yearTo,
)

private fun AnimeSearchFilters.toLibraryFilters(catalog: LibraryFilterCatalog): LibrarySearchFilters =
    LibrarySearchFilters(
        type = typeAlias?.let { alias -> catalog.typeOptions.firstOrNull { it.equals(alias, ignoreCase = true) } },
        status = statusAlias?.let { alias -> catalog.statusOptions.firstOrNull { libraryStatusAlias(it) == alias } },
        includedGenres = includedGenreAliases,
        excludedGenres = excludedGenreAliases,
        yearFrom = yearFrom,
        yearTo = yearTo,
    )

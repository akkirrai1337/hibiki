package org.akkirrai.hibiki.shared.library.screen
import org.akkirrai.hibiki.shared.library.*
import org.akkirrai.hibiki.shared.library.ui.isRussianLibraryLanguage

import org.akkirrai.hibiki.shared.catalog.filters.*

import org.akkirrai.hibiki.shared.app.libraryText

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.akkirrai.beakokit.api.AnimeKey
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.design.component.source.AppSourceBadge
import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.library.LibraryEntry
import org.akkirrai.hibiki.shared.library.state.LibraryUiState
import org.akkirrai.hibiki.shared.library.state.buildLibraryFilterCatalog
import org.akkirrai.hibiki.shared.library.ui.isRussianLibraryLanguage
import org.akkirrai.hibiki.shared.library.state.toAnimeSearchFilters
import org.akkirrai.hibiki.shared.library.state.toLibrarySearchFilters
import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.catalog.filters.AppCatalogFilterSheet
import org.akkirrai.hibiki.shared.catalog.filters.defaultCatalogFilterYearRange
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor
import org.akkirrai.hibiki.shared.source.AppSourceIconImage
import org.akkirrai.hibiki.shared.settings.LanguageMode
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText

@Composable
internal fun ColumnScope.LibraryScreen(
    entries: List<LibraryEntry>,
    sources: List<AppSourceDescriptor>,
    state: LibraryUiState,
    onAnimeClick: (Anime) -> Unit,
    onCategorySelected: (LibraryCategory) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchClear: () -> Unit,
    onFiltersApply: (org.akkirrai.hibiki.shared.library.state.LibrarySearchFilters) -> Unit,
    filterOverlayOpen: Boolean,
    onFilterOpen: () -> Unit,
    onFilterVisibilityChange: (Boolean) -> Unit,
    languageMode: LanguageMode,
    systemLanguage: String,
    bottomContentPadding: Dp,
) {
    val isRussian = isRussianLibraryLanguage(languageMode, systemLanguage)
    val categoryLabels = LibraryCategory.entries.associateWith { it.libraryText() }
    val sourcesById = remember(sources) { sources.associateBy(AppSourceDescriptor::id) }
    AppLibraryScreen(
        state = state,
        labels = AppLibraryScreenLabels(
            searchPlaceholder = appText(AppTextKey.SearchPlaceholder),
            filterContentDescription = appText(AppTextKey.SearchFilters),
            clearContentDescription = appText(AppTextKey.Back),
            categoryLabels = categoryLabels,
            emptyTitle = appText(AppTextKey.LibraryEmptyTitle),
            emptyMessage = appText(AppTextKey.LibraryEmptyBody),
            filteredTitle = appText(AppTextKey.LibraryFilteredEmptyTitle),
            searchTitle = appText(AppTextKey.LibrarySearchEmptyTitle),
            filteredMessage = appText(AppTextKey.LibraryFilteredEmptyBody),
            categoryEmptyLabels = categoryLabels,
            announcementLabel = appText(AppTextKey.Announcement),
            movieLabel = appText(AppTextKey.Type),
            libraryStatusLabel = { category -> categoryLabels.getValue(category) },
        ),
        bottomContentPadding = bottomContentPadding,
        onAnimeClick = onAnimeClick,
        onSearchQueryChange = onSearchQueryChange,
        onClearSearch = onSearchClear,
        onFilterClick = onFilterOpen,
        onCategorySelected = onCategorySelected,
        entryContent = { entry, entryModifier ->
            AppLibraryEntryCard(
                entry = entry,
                announcementLabel = appText(AppTextKey.Announcement),
                movieLabel = appText(AppTextKey.Type),
                onClick = { onAnimeClick(entry.anime) },
                libraryStatusLabel = { category -> categoryLabels.getValue(category) },
                sourceBadgeContent = { titleId ->
                    AnimeKey.parse(titleId)?.sourceId?.value
                        ?.let(sourcesById::get)
                        ?.let { source ->
                            AppSourceBadge(
                                title = source.name,
                                iconContent = { iconModifier ->
                                    AppSourceIconImage(
                                        url = source.iconUrl,
                                        sourceId = source.id,
                                        modifier = iconModifier,
                                    )
                                },
                            )
                        }
                },
                modifier = entryModifier,
            )
        },
        filterContent = { onDismiss ->
            AppCatalogFilterSheet(
                initialFilters = state.searchFilters.toAnimeSearchFilters(),
                filterCatalog = buildLibraryFilterCatalog(
                    typeOptions = state.filterCatalog.typeOptions,
                    statusOptions = state.filterCatalog.statusOptions,
                    genreOptions = state.filterCatalog.genreOptions,
                    isRussian = isRussian,
                ),
                isFilterCatalogLoading = false,
                onApply = { filters ->
                    onFiltersApply(filters.toLibrarySearchFilters(state.filterCatalog))
                    onDismiss()
                },
                onDismissRequest = onDismiss,
                unavailableLabel = appText(AppTextKey.FilterUnavailable),
                typeTitle = appText(AppTextKey.Type),
                genresTitle = appText(AppTextKey.Genres),
                yearTitle = appText(AppTextKey.ReleaseDate),
                yearAllLabel = appText(AppTextKey.FilterAllYears),
                yearFromLabel = appText(AppTextKey.FilterFromYear),
                yearToLabel = appText(AppTextKey.FilterToYear),
                statusTitle = appText(AppTextKey.Status),
                resetLabel = appText(AppTextKey.FilterReset),
                applyLabel = appText(AppTextKey.FilterApply),
                defaultYearRange = defaultCatalogFilterYearRange(
                    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year,
                ),
                optionText = { it.title },
                shape = RoundedCornerShape(UiDimens.LargeCorner),
                maxCollapsedGenreGroups = 3,
                maxCollapsedGenreItems = null,
            )
        },
        filterVisible = filterOverlayOpen,
        onFilterVisibilityChange = onFilterVisibilityChange,
        modifier = Modifier.fillMaxSize(),
    )
}

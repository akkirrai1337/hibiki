package org.akkirrai.hibiki.library.screen
import org.akkirrai.hibiki.library.*

import org.akkirrai.hibiki.catalog.filters.*

import org.akkirrai.hibiki.app.libraryText
import org.akkirrai.hibiki.library.ui.isRussianLibraryLanguage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.design.component.content.AppPosterAnimeCard
import org.akkirrai.hibiki.design.component.poster.AppImagePlaceholder
import org.akkirrai.hibiki.design.component.poster.AppPosterImage
import org.akkirrai.hibiki.catalog.model.buildCardMeta
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.akkirrai.beakokit.api.AnimeKey
import org.akkirrai.hibiki.design.UiDimens
import org.akkirrai.hibiki.design.component.source.AppSourceBadge
import org.akkirrai.hibiki.library.state.LibraryUiState
import org.akkirrai.hibiki.library.state.buildLibraryFilterCatalog
import org.akkirrai.hibiki.library.state.toAnimeSearchFilters
import org.akkirrai.hibiki.library.state.toLibrarySearchFilters
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.core.source.AppSourceDescriptor
import org.akkirrai.hibiki.core.source.AppSourceIconImage
import org.akkirrai.hibiki.app.settings.LanguageMode
import org.akkirrai.hibiki.text.AppTextKey
import org.akkirrai.hibiki.text.appText

@Composable
internal fun ColumnScope.LibraryRoute(
    sources: List<AppSourceDescriptor>,
    state: LibraryUiState,
    actions: LibraryActions,
    listState: LazyListState,
    onFiltersApply: (org.akkirrai.hibiki.library.state.LibrarySearchFilters) -> Unit,
    filterOverlayOpen: Boolean,
    languageMode: LanguageMode,
    systemLanguage: String,
    bottomContentPadding: Dp,
) {
    val isRussian = isRussianLibraryLanguage(languageMode, systemLanguage)
    val sourcesById = remember(sources) { sources.associateBy(AppSourceDescriptor::id) }
    LibraryScreen(
        state = state,
        actions = actions,
        listState = listState,
        bottomContentPadding = bottomContentPadding,
        entryContent = { entry, entryModifier ->
            AppLibraryEntryCard(
                entry = entry,
                announcementLabel = appText(AppTextKey.Announcement),
                movieLabel = appText(AppTextKey.Type),
                onClick = { actions.onAnimeClick(entry.anime) },
                libraryStatusLabel = { category -> category.libraryText() },
                sourceBadgeContent = { titleId ->
                    AnimeKey.parse(titleId)?.sourceId?.value
                        ?.let(sourcesById::get)
                        ?.let { source ->
                            AppSourceBadge(
                                title = source.name,
                                iconContent = { iconModifier ->
                                    AppSourceIconImage(
                                        url = source.iconUrl,
                                        modifier = iconModifier,
                                        debugTag = "library-badge",
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
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun AppLibraryAnimeCard(
    anime: Anime,
    metaText: String,
    onClick: () -> Unit,
    posterContent: @Composable BoxScope.() -> Unit,
    sourceBadgeContent: (@Composable () -> Unit)? = null,
    posterFooterContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppPosterAnimeCard(
        anime = anime,
        metaText = metaText,
        onClick = onClick,
        modifier = modifier,
        posterContent = {
            posterContent()
            sourceBadgeContent?.let { content ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(LibraryAnimeCardSourceBadgePadding),
                ) {
                    content()
                }
            }
        },
        posterFooterContent = posterFooterContent,
    )
}

@Composable
private fun AppLibraryEntryCard(
    entry: LibraryEntry,
    announcementLabel: String,
    movieLabel: String,
    onClick: () -> Unit,
    libraryStatusLabel: @Composable (LibraryCategory) -> String,
    sourceBadgeContent: (@Composable (String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val anime = entry.anime
    AppLibraryAnimeCard(
        anime = anime,
        metaText = anime.buildCardMeta(
            announcementLabel = announcementLabel,
            movieLabel = movieLabel,
        ),
        onClick = onClick,
        modifier = modifier,
        posterContent = {
            AppPosterImage(
                primaryUrl = anime.posterUrl,
                fallbackUrl = anime.posterFallbackUrl,
                contentDescription = anime.title,
                modifier = Modifier.fillMaxSize(),
                placeholder = { AppImagePlaceholder() },
            )
        },
        sourceBadgeContent = sourceBadgeContent?.let { badge ->
            { badge(anime.id) }
        },
        posterFooterContent = {
            LibraryStatusPosterFooter(
                label = libraryStatusLabel(entry.category),
                icon = entry.category.icon(),
            )
        },
    )
}

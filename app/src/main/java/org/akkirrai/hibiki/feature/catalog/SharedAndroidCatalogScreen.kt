package org.akkirrai.hibiki.feature.catalog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Year
import me.saket.cascade.CascadeDropdownMenu
import me.saket.cascade.rememberCascadeState
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.app.settings.LocalAppLanguage
import org.akkirrai.hibiki.app.settings.withLanguage
import org.akkirrai.hibiki.core.design.component.rememberLibraryStatusByAnimeId
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.source.labelResId
import org.akkirrai.hibiki.shared.catalog.AppCatalogPaginationEffect
import org.akkirrai.hibiki.shared.catalog.AppCatalogQueryEffect
import org.akkirrai.hibiki.shared.catalog.AppCatalogScreen
import org.akkirrai.hibiki.shared.catalog.AppCatalogScreenLabels
import org.akkirrai.hibiki.shared.catalog.AppCatalogSortMenuContent
import org.akkirrai.hibiki.shared.catalog.AppCatalogSortOrderIcon
import org.akkirrai.hibiki.shared.catalog.CatalogSort
import org.akkirrai.hibiki.shared.catalog.CatalogSortMenuCornerRadius
import org.akkirrai.hibiki.shared.catalog.CatalogSortMenuOffsetY
import org.akkirrai.hibiki.shared.catalog.CatalogSortMenuWidth
import org.akkirrai.hibiki.shared.catalog.catalogSortFromAlias
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilterOption

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SharedAndroidCatalogScreen(
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 0.dp,
    viewModel: CatalogViewModel = viewModel(
        factory = CatalogViewModel.Factory(LocalContext.current),
    ),
) {
    val state by viewModel.uiState.collectAsState()
    val selectedSourceId = LocalAppPreferencesState.current.animeSource
    val listState = rememberSaveable(selectedSourceId, saver = LazyListState.Saver) {
        LazyListState()
    }
    val libraryStatusByAnimeId = rememberLibraryStatusByAnimeId()
    val appLanguage = LocalAppLanguage.current
    val baseContext = LocalContext.current
    val localizedContext = remember(baseContext, appLanguage) { baseContext.withLanguage(appLanguage) }
    val sortLabels = remember(localizedContext) {
        CatalogSort.entries.associateWith { sort -> localizedContext.getString(sort.labelRes) }
    }

    AppCatalogQueryEffect(query = state.query, onQuerySettled = viewModel::load)
    AppCatalogPaginationEffect(listState = listState, state = state, onLoadMore = viewModel::loadMore)

    AppCatalogScreen(
        state = state,
        listState = listState,
        bottomContentPadding = bottomContentPadding,
        currentYear = Year.now().value,
        libraryStatusByAnimeId = libraryStatusByAnimeId,
        labels = sharedAndroidCatalogLabels(sortLabels),
        onQueryChange = viewModel::updateQuery,
        onRetry = viewModel::load,
        onLoadMoreRetry = viewModel::loadMore,
        onItemVisible = viewModel::enrichDescription,
        onSortSelected = viewModel::selectSort,
        onFiltersApply = viewModel::applyFilters,
        onAnimeClick = onAnimeClick,
        sortMenuContent = { selectedSort, availableSorts, expanded, onExpandedChange, onSortSelected, title, label ->
            AndroidCatalogSortMenu(
                selectedSort = selectedSort,
                availableSorts = availableSorts,
                expanded = expanded,
                onExpandedChange = onExpandedChange,
                onSortSelected = onSortSelected,
                title = title,
                label = label,
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun AndroidCatalogSortMenu(
    selectedSort: CatalogSort,
    availableSorts: List<CatalogSort>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSortSelected: (CatalogSort) -> Unit,
    title: String,
    label: (CatalogSort) -> String,
) {
    val cascadeState = rememberCascadeState()
    val haptic = LocalHapticFeedback.current
    val layoutDirection = LocalLayoutDirection.current
    val screenWidth = LocalWindowInfo.current.containerSize.width
    val density = androidx.compose.ui.platform.LocalDensity.current
    val screenWidthDp = with(density) { screenWidth.toDp() }
    val horizontalInsets = org.akkirrai.hibiki.shared.design.UiDimens.ScreenPadding * 2
    val offsetX = (screenWidthDp - horizontalInsets - CatalogSortMenuWidth) / 2

    CascadeDropdownMenu(
        expanded = expanded,
        onDismissRequest = { onExpandedChange(false) },
        state = cascadeState,
        offset = DpOffset(
            x = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) offsetX else -offsetX,
            y = CatalogSortMenuOffsetY,
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(CatalogSortMenuCornerRadius),
    ) {
        AppCatalogSortMenuContent(
            title = title,
            sorts = availableSorts,
            selectedSort = selectedSort,
            label = label,
            expanded = expanded,
            onSortSelected = {
                haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                onExpandedChange(false)
                onSortSelected(it)
            },
            orderContent = { atEnd, orderModifier ->
                AppCatalogSortOrderIcon(atEnd = atEnd, modifier = orderModifier)
            },
        )
    }
}

@Composable
private fun sharedAndroidCatalogLabels(
    sortLabels: Map<CatalogSort, String>,
) = AppCatalogScreenLabels(
    errorTitle = stringResource(R.string.catalog_error_title),
    retryLabel = stringResource(R.string.search_retry),
    announcementLabel = stringResource(R.string.anime_meta_announcement),
    movieLabel = stringResource(R.string.anime_meta_movie),
    searchPlaceholder = stringResource(R.string.search_placeholder),
    filterContentDescription = stringResource(R.string.search_filters),
    clearContentDescription = stringResource(R.string.home_search_clear),
    sortTitle = stringResource(R.string.catalog_sort_title),
    sortLabels = sortLabels,
    filterUnavailable = stringResource(R.string.search_filters_unavailable),
    typeTitle = stringResource(R.string.search_filters_type),
    genresTitle = stringResource(R.string.search_filters_genres),
    yearTitle = stringResource(R.string.search_filters_year),
    yearAllLabel = stringResource(R.string.search_filters_year_all),
    yearFromLabel = stringResource(R.string.search_filters_year_from),
    yearToLabel = stringResource(R.string.search_filters_year_to),
    statusTitle = stringResource(R.string.search_filters_status),
    resetLabel = stringResource(R.string.search_filters_reset),
    applyLabel = stringResource(R.string.search_filters_apply),
    libraryStatusLabel = { category -> stringResource(category.labelResId) },
    optionText = { option: AnimeCatalogFilterOption -> option.title },
)

private val CatalogSort.labelRes: Int
    get() = when (this) {
        CatalogSort.Alphabetical -> R.string.catalog_sort_alphabetical
        CatalogSort.Popular -> R.string.catalog_sort_popular
        CatalogSort.Updated -> R.string.catalog_sort_updated
    }

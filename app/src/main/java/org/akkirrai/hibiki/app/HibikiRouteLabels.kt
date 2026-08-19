package org.akkirrai.hibiki.app

import androidx.compose.runtime.Composable
import org.akkirrai.hibiki.catalog.screen.AppCatalogScreenLabels
import org.akkirrai.hibiki.catalog.sort.CatalogSort
import org.akkirrai.hibiki.home.screen.AppHomeScreenLabels
import org.akkirrai.hibiki.library.LibraryCategory
import org.akkirrai.hibiki.app.settings.NotificationPermissionState
import org.akkirrai.hibiki.text.AppTextKey
import org.akkirrai.hibiki.text.appSearchResultsCount
import org.akkirrai.hibiki.text.appText

@Composable
internal fun LibraryCategory.libraryText(): String = when (this) {
    LibraryCategory.Watching -> appText(AppTextKey.LibraryWatching)
    LibraryCategory.Planned -> appText(AppTextKey.LibraryPlanned)
    LibraryCategory.Completed -> appText(AppTextKey.LibraryCompleted)
    LibraryCategory.Dropped -> appText(AppTextKey.LibraryDropped)
    LibraryCategory.OnHold -> appText(AppTextKey.LibraryOnHold)
    LibraryCategory.Favorite -> appText(AppTextKey.LibraryFavorite)
    LibraryCategory.Saved -> appText(AppTextKey.LibrarySaved)
    // Never actually rendered -- Recent is filtered out of every category list/tab/picker
    // before a label lookup happens. Falls back to the closest real label just in case.
    LibraryCategory.Recent -> appText(AppTextKey.LibraryWatching)
}

internal fun NotificationPermissionState.textKey(): AppTextKey = when (this) {
    NotificationPermissionState.NOT_ASKED -> AppTextKey.SettingsNotificationsStatus
    NotificationPermissionState.GRANTED -> AppTextKey.SettingsNotificationsGranted
    NotificationPermissionState.DENIED -> AppTextKey.SettingsNotificationsDenied
}

@Composable
internal fun defaultCatalogScreenLabels(): AppCatalogScreenLabels {
    val categoryLabels = LibraryCategory.entries.associateWith { it.libraryText() }
    return AppCatalogScreenLabels(
        errorTitle = appText(AppTextKey.CatalogError),
        retryLabel = appText(AppTextKey.SearchRetry),
        announcementLabel = appText(AppTextKey.Announcement),
        movieLabel = appText(AppTextKey.Type),
        searchPlaceholder = appText(AppTextKey.SearchPlaceholder),
        filterContentDescription = appText(AppTextKey.SearchFilters),
        clearContentDescription = appText(AppTextKey.Back),
        sortTitle = appText(AppTextKey.CatalogSortTitle),
        sortLabels = mapOf(
            CatalogSort.Alphabetical to appText(AppTextKey.CatalogSortAlphabetical),
            CatalogSort.Popular to appText(AppTextKey.CatalogSortPopular),
            CatalogSort.Updated to appText(AppTextKey.CatalogSortUpdated),
        ),
        filterUnavailable = appText(AppTextKey.FilterUnavailable),
        typeTitle = appText(AppTextKey.Type),
        genresTitle = appText(AppTextKey.Genres),
        yearTitle = appText(AppTextKey.ReleaseDate),
        yearAllLabel = appText(AppTextKey.FilterAllYears),
        yearFromLabel = appText(AppTextKey.FilterFromYear),
        yearToLabel = appText(AppTextKey.FilterToYear),
        statusTitle = appText(AppTextKey.Status),
        resetLabel = appText(AppTextKey.FilterReset),
        applyLabel = appText(AppTextKey.FilterApply),
        libraryStatusLabel = { category -> categoryLabels.getValue(category) },
        optionText = { it.title },
    )
}

@Composable
internal fun defaultHomeScreenLabels(): AppHomeScreenLabels {
    val categoryLabels = LibraryCategory.entries.associateWith { it.libraryText() }
    return AppHomeScreenLabels(
        searchPlaceholder = appText(AppTextKey.SearchPlaceholder),
        searchFilters = appText(AppTextKey.SearchFilters),
        searchClear = appText(AppTextKey.Back),
        searchLoadMore = appText(AppTextKey.HomeSearchLoadMore),
        searchEmptyTitle = appText(AppTextKey.HomeSearchEmptyTitle),
        searchEmptyMessage = appText(AppTextKey.HomeSearchEmptyBody),
        resultsCountLabel = ::appSearchResultsCount,
        continueTitle = appText(AppTextKey.HomeContinueTitle),
        continueEmptyTitle = appText(AppTextKey.HomeContinueEmptyTitle),
        continueEmptyMessage = appText(AppTextKey.HomeContinueEmptyBody),
        continueOpenHint = appText(AppTextKey.HomeContinueOpenHint),
        recentlyWatchedTitle = appText(AppTextKey.HomeRecentlyWatched),
        recentlyAddedTitle = appText(AppTextKey.HomeRecentlyAdded),
        announcementLabel = appText(AppTextKey.Announcement),
        movieLabel = appText(AppTextKey.Type),
        personalEmptyTitle = appText(AppTextKey.HomePersonalEmptyTitle),
        personalEmptyMessage = appText(AppTextKey.HomePersonalEmptyBody),
        personalEmptyActionLabel = appText(AppTextKey.HomeBrowseCatalog),
        filterUnavailable = appText(AppTextKey.FilterUnavailable),
        typeTitle = appText(AppTextKey.Type),
        genresTitle = appText(AppTextKey.Genres),
        yearTitle = appText(AppTextKey.ReleaseDate),
        yearAllLabel = appText(AppTextKey.FilterAllYears),
        yearFromLabel = appText(AppTextKey.FilterFromYear),
        yearToLabel = appText(AppTextKey.FilterToYear),
        statusTitle = appText(AppTextKey.Status),
        resetLabel = appText(AppTextKey.FilterReset),
        applyLabel = appText(AppTextKey.FilterApply),
        libraryStatusLabel = { category -> categoryLabels.getValue(category) },
        optionText = { it.title },
    )
}

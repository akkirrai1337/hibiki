package org.akkirrai.hibiki.shared.app.shell.home

import org.akkirrai.hibiki.shared.home.presentation.HomeSearchPresenter
import org.akkirrai.hibiki.shared.search.model.AnimeSearchFilters

internal class HibikiHomeSearchActions(
    presenter: HomeSearchPresenter,
) {
    val onQueryChange: (String) -> Unit = presenter::onQueryChange
    val onClear: () -> Unit = presenter::clearSearch
    val onFilterApply: (AnimeSearchFilters) -> Unit = presenter::applyFilters
    val onLoadMore: () -> Unit = presenter::loadMore
    val onRetry: () -> Unit = presenter::retrySearch
}

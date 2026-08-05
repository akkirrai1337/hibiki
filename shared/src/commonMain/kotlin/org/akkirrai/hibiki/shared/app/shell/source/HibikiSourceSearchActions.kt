package org.akkirrai.hibiki.shared.app.shell.source

import org.akkirrai.hibiki.shared.catalog.presentation.SourcesSearchPresenter

internal class HibikiSourceSearchActions(
    presenter: SourcesSearchPresenter,
) {
    val onQueryChange: (String) -> Unit = presenter::onQueryChange
    val onClear: () -> Unit = presenter::clear
    val onRetry: () -> Unit = presenter::search
    val onRetryForSource: (String) -> Unit = presenter::retry
}

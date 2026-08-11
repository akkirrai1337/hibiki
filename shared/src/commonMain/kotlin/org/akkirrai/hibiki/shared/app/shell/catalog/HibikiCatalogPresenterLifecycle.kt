package org.akkirrai.hibiki.shared.app.shell.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import org.akkirrai.hibiki.shared.catalog.presentation.AnimeCatalogPresenter
import org.akkirrai.hibiki.shared.catalog.presentation.SourcesSearchPresenter
import org.akkirrai.hibiki.shared.home.presentation.HomeSearchPresenter

@Composable
internal fun HibikiCatalogPresenterLifecycle(
    presenter: AnimeCatalogPresenter,
    homeSearchPresenter: HomeSearchPresenter,
    sourceSearchPresenter: SourcesSearchPresenter,
    catalogRefreshKey: Any?,
    catalogReady: Boolean,
    catalogVisible: Boolean,
    onDisposed: () -> Unit,
) {
    LaunchedEffect(presenter, catalogRefreshKey, catalogReady, catalogVisible) {
        if (!catalogReady || !catalogVisible) return@LaunchedEffect
        presenter.loadFilterCatalog()
        presenter.search()
    }
    DisposableEffect(presenter) {
        homeSearchPresenter.loadFilterCatalog()
        onDispose {
            presenter.close()
            homeSearchPresenter.close()
            sourceSearchPresenter.close()
            onDisposed()
        }
    }
}

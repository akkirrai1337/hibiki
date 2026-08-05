package org.akkirrai.hibiki.shared.app.shell.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import org.akkirrai.hibiki.shared.catalog.presentation.AnimeCatalogPresenter
import org.akkirrai.hibiki.shared.catalog.presentation.SourcesSearchPresenter
import org.akkirrai.hibiki.shared.home.presentation.HomeSearchPresenter

@Composable
internal fun HibikiCatalogPresenterLifecycle(
    presenter: AnimeCatalogPresenter,
    homeSearchPresenter: HomeSearchPresenter,
    sourceSearchPresenter: SourcesSearchPresenter,
    onDisposed: () -> Unit,
) {
    DisposableEffect(presenter) {
        presenter.loadFilterCatalog()
        presenter.search()
        homeSearchPresenter.loadFilterCatalog()
        onDispose {
            presenter.close()
            homeSearchPresenter.close()
            sourceSearchPresenter.close()
            onDisposed()
        }
    }
}

package org.akkirrai.hibiki.shared.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogRepository
import org.akkirrai.hibiki.shared.catalog.PrototypeAnimeCatalogRepository
import org.akkirrai.hibiki.shared.prototype.HibikiAppShell
import org.akkirrai.hibiki.shared.settings.AppSettingsStore
import org.akkirrai.hibiki.shared.settings.InMemoryAppSettingsStore

/** Canonical shared application entry point for platform hosts. */
@Composable
fun HibikiApp(
    modifier: Modifier = Modifier,
    repository: AnimeCatalogRepository = PrototypeAnimeCatalogRepository,
    settingsStore: AppSettingsStore = InMemoryAppSettingsStore(),
) {
    HibikiAppShell(
        modifier = modifier,
        repository = repository,
        settingsStore = settingsStore,
    )
}

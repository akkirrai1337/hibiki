package org.akkirrai.hibiki.shared.app.destination.actions

import org.akkirrai.hibiki.shared.catalog.model.Anime

internal data class AppDestinationNavigationActions(
    val onAnimeClick: (Anime) -> Unit,
    val onBackFromDetails: () -> Unit,
    val onWatchClick: (Anime) -> Unit,
    val onBackFromWatch: () -> Unit,
    val onProfileSettingsClick: () -> Unit,
    val onSourceSelected: (String) -> Unit,
    val onBrowseCatalog: () -> Unit,
    val onOpenLibrary: () -> Unit,
    val onSourceRepositoriesClick: () -> Unit,
    val onSourceRepositoryClick: (String) -> Unit,
    val onSourcePackageInfoClick: (String, String) -> Unit,
    val onSettingsBack: () -> Unit,
)

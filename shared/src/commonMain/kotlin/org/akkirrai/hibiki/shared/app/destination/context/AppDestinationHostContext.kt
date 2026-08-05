package org.akkirrai.hibiki.shared.app.destination.context

import androidx.compose.ui.Modifier

internal data class AppDestinationHostContext(
    val systemLanguage: String,
    val includeNavigationBarPadding: Boolean,
    val onLibraryChanged: () -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onGitHubClick: () -> Unit,
    val modifier: Modifier,
)

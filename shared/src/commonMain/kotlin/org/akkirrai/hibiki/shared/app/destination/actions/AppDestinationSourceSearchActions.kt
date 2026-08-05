package org.akkirrai.hibiki.shared.app.destination.actions

internal data class AppDestinationSourceSearchActions(
    val onQueryChange: (String) -> Unit,
    val onClear: () -> Unit,
    val onRetry: () -> Unit,
    val onRetryForSource: (String) -> Unit,
)

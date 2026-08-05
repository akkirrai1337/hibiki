package org.akkirrai.hibiki.shared.app.destination.context

import androidx.compose.foundation.lazy.LazyListState

internal data class AppDestinationSettingsListsState(
    val settings: LazyListState,
    val externalSources: LazyListState,
)

package org.akkirrai.hibiki.shared.app.destination.state

import androidx.compose.foundation.lazy.LazyListState
import org.akkirrai.hibiki.shared.home.presentation.HomeSearchUiState
import org.akkirrai.hibiki.shared.home.state.HomeUiState

internal data class AppDestinationHomeState(
    val ui: HomeUiState,
    val search: HomeSearchUiState,
    val listState: LazyListState,
)

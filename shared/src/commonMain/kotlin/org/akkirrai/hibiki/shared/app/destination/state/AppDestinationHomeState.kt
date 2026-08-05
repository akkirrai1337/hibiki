package org.akkirrai.hibiki.shared.app.destination.state

import org.akkirrai.hibiki.shared.home.presentation.HomeSearchUiState
import org.akkirrai.hibiki.shared.home.state.HomeUiState

internal data class AppDestinationHomeState(
    val ui: HomeUiState,
    val search: HomeSearchUiState,
)

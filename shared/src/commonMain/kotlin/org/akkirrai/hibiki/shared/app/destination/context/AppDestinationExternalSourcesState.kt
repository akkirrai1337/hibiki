package org.akkirrai.hibiki.shared.app.destination.context

import org.akkirrai.hibiki.shared.source.ExternalSourceRepositoryController
import org.akkirrai.hibiki.shared.source.ExternalSourceRepositoryUiState

internal data class AppDestinationExternalSourcesState(
    val repository: ExternalSourceRepositoryUiState?,
    val controller: ExternalSourceRepositoryController?,
)

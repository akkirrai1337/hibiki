package org.akkirrai.hibiki.shared.app.destination.state

import org.akkirrai.hibiki.shared.source.SourcesSearchUiState
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor

internal data class AppDestinationSourceState(
    val sources: List<AppSourceDescriptor>,
    val selectedSourceId: String?,
    val search: SourcesSearchUiState,
)

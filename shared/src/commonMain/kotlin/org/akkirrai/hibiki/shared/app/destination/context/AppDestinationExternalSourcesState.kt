package org.akkirrai.hibiki.shared.app.destination.context

import org.akkirrai.hibiki.shared.source.ExternalSourceRepositoryController
import org.akkirrai.hibiki.shared.source.ExternalSourceRepositoryUiState

internal data class AppDestinationExternalSourcesState(
    val repository: ExternalSourceRepositoryUiState?,
    val controller: ExternalSourceRepositoryController?,
    val selectedTab: Int = 0,
    val onSelectedTabChange: (Int) -> Unit = {},
    val readClipboardText: () -> String? = { null },
    val copyText: (String) -> Unit = {},
)

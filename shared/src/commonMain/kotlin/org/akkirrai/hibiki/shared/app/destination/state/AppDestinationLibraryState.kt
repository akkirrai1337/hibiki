package org.akkirrai.hibiki.shared.app.destination.state

import org.akkirrai.hibiki.shared.library.LibraryEntry
import org.akkirrai.hibiki.shared.library.state.LibraryUiState

internal data class AppDestinationLibraryState(
    val entries: List<LibraryEntry>,
    val ui: LibraryUiState,
    val filterOverlayOpen: Boolean,
)

package org.akkirrai.hibiki.shared.app.destination.state

internal data class AppDestinationDetailsOverlayState(
    val posterPreviewOpen: Boolean?,
    val onPosterPreviewOpenChange: ((Boolean) -> Unit)?,
    val titleSheetOpen: Boolean?,
    val onTitleSheetOpenChange: ((Boolean) -> Unit)?,
    val librarySheetOpen: Boolean?,
    val onLibrarySheetOpenChange: ((Boolean) -> Unit)?,
)

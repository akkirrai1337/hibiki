package org.akkirrai.hibiki.details.state

enum class DetailsOverlayBackTarget {
    Library,
    Title,
    Poster,
    None,
}

fun detailsOverlayBackTarget(
    librarySheetOpen: Boolean,
    titleSheetOpen: Boolean,
    posterPreviewOpen: Boolean,
): DetailsOverlayBackTarget = when {
    librarySheetOpen -> DetailsOverlayBackTarget.Library
    titleSheetOpen -> DetailsOverlayBackTarget.Title
    posterPreviewOpen -> DetailsOverlayBackTarget.Poster
    else -> DetailsOverlayBackTarget.None
}

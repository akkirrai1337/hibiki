package org.akkirrai.hibiki.shared.details

import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.TitleWatchState

/** Platform-neutral state required to render the details screen. */
data class DetailsUiState(
    val anime: Anime,
    val libraryCategory: LibraryCategory? = null,
    val resumeState: TitleWatchState? = null,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

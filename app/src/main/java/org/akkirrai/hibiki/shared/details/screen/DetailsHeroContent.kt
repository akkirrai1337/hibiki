package org.akkirrai.hibiki.shared.details.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AppDetailsHeroContent(
    posterExpanded: Boolean,
    isInLibrary: Boolean,
    canWatch: Boolean,
    libraryLabel: String,
    watchLabel: String,
    onPosterClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onPrimaryClick: () -> Unit,
    posterContent: @Composable () -> Unit,
    mediaContent: @Composable (Modifier) -> Unit,
    textContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppDetailsHeroSection(
        posterExpanded = posterExpanded,
        onPosterClick = onPosterClick,
        posterContent = posterContent,
        mediaContent = mediaContent,
        textContent = textContent,
        actionsContent = {
            DetailsHeroActions(
                isInLibrary = isInLibrary,
                canWatch = canWatch,
                libraryLabel = libraryLabel,
                watchLabel = watchLabel,
                onLibraryClick = onLibraryClick,
                onPrimaryClick = onPrimaryClick,
            )
        },
        modifier = modifier,
    )
}

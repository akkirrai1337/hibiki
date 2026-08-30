package org.akkirrai.hibiki.core.design.component.anime

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Shared-element keys for the Home feed to Details navigation transition. */
internal object AnimeDetailsTransition {
    fun cardKey(animeId: String): String = "anime_details_card_$animeId"

    fun posterKey(animeId: String): String = "anime_details_poster_$animeId"
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun animeDetailsSharedCardModifier(
    animeId: String,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
): Modifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
    with(sharedTransitionScope) {
        Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(AnimeDetailsTransition.cardKey(animeId)),
            animatedVisibilityScope = animatedVisibilityScope,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
} else {
    Modifier
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun animeDetailsSharedPosterModifier(
    animeId: String,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
): Modifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
    with(sharedTransitionScope) {
        Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(AnimeDetailsTransition.posterKey(animeId)),
            animatedVisibilityScope = animatedVisibilityScope,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
} else {
    Modifier
}

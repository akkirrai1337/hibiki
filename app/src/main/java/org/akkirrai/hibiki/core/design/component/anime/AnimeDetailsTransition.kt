package org.akkirrai.hibiki.core.design.component.anime

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Shared-element keys for the Home feed to Details navigation transition. */
internal object AnimeDetailsTransition {
    fun cardKey(animeId: String, origin: String): String = "anime_details_card_${origin}_$animeId"

    fun posterKey(animeId: String, origin: String): String = "anime_details_poster_${origin}_$animeId"
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun animeDetailsSharedCardModifier(
    animeId: String,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    origin: String = DEFAULT_DETAILS_TRANSITION_ORIGIN,
): Modifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
    with(sharedTransitionScope) {
        Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(AnimeDetailsTransition.cardKey(animeId, origin)),
            animatedVisibilityScope = animatedVisibilityScope,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            // The app bottom bar is a sibling of the navigation host. Rendering this card in the
            // shared-transition overlay bypasses that sibling's z-order, making the card slide
            // across the bottom bar while opening details.
            renderInOverlayDuringTransition = false,
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
    origin: String = DEFAULT_DETAILS_TRANSITION_ORIGIN,
): Modifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
    with(sharedTransitionScope) {
        Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(AnimeDetailsTransition.posterKey(animeId, origin)),
            animatedVisibilityScope = animatedVisibilityScope,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            renderInOverlayDuringTransition = false,
        )
    }
} else {
    Modifier
}

internal const val DEFAULT_DETAILS_TRANSITION_ORIGIN = "default"

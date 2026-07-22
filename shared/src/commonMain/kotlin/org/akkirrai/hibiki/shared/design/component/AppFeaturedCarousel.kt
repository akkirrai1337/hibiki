package org.akkirrai.hibiki.shared.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.model.Anime

private const val FEATURED_AUTO_ADVANCE_MS = 8_000

@Composable
fun AppFeaturedCarousel(
    items: List<Anime>,
    featuredLabel: String,
    metaText: @Composable (Anime) -> String,
    onAnimeClick: (Anime) -> Unit,
    autoAdvanceEnabled: Boolean,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 240.dp,
    imageContent: @Composable BoxScope.(Anime) -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { items.size },
    )
    val progress = remember { Animatable(0f) }
    var indicatorPage by remember { mutableStateOf(0) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collect { page ->
                if (page != indicatorPage) {
                    progress.stop()
                    progress.snapTo(0f)
                    indicatorPage = page
                }
            }
    }

    var timerRestartKey by remember { mutableStateOf(0) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling) timerRestartKey++
            }
    }

    LaunchedEffect(timerRestartKey, items.size, autoAdvanceEnabled) {
        if (!autoAdvanceEnabled || items.size <= 1) {
            progress.stop()
            return@LaunchedEffect
        }

        if (pagerState.isScrollInProgress) {
            snapshotFlow { pagerState.isScrollInProgress }
                .first { !it }
        }

        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = FEATURED_AUTO_ADVANCE_MS,
                easing = LinearEasing,
            ),
        )

        if (!pagerState.isScrollInProgress && pagerState.settledPage == indicatorPage) {
            val nextPage = (indicatorPage + 1) % items.size
            progress.snapTo(0f)
            pagerState.animateScrollToPage(nextPage)
        }
    }

    Box(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            contentPadding = PaddingValues(horizontal = UiDimens.ScreenPadding),
            pageSpacing = 12.dp,
            beyondViewportPageCount = 1,
        ) { page ->
            val anime = items[page]
            AppFeaturedCard(
                anime = anime,
                featuredLabel = featuredLabel,
                meta = metaText(anime),
                height = height,
                onClick = { onAnimeClick(anime) },
                imageContent = { imageContent(anime) },
            )
        }

        if (items.size > 1) {
            AppPageIndicator(
                totalPages = items.size,
                currentPage = indicatorPage,
                progress = progress.value,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
            )
        }
    }
}

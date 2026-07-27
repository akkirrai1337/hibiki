package org.akkirrai.hibiki.shared.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

@Composable
fun AppProfileBannerLayout(
    banner: @Composable BoxScope.(Float, Modifier) -> Unit,
    bannerElevatedContent: @Composable BoxScope.(Float, Modifier) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    maxBannerHeight: Dp = ProfileBannerMaxHeight,
    contentPadding: PaddingValues = PaddingValues(),
    contentBackgroundColor: Color = Color.Transparent,
    minBannerPadding: Dp = ProfileBannerMinPadding,
) {
    val density = LocalDensity.current
    var bannerHeightPx by remember { mutableFloatStateOf(with(density) { maxBannerHeight.toPx() }) }
    var ratio by remember { mutableFloatStateOf(1f) }
    val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val minBannerHeightPx = with(density) { (statusBarHeight + minBannerPadding).toPx() }
    val maxBannerHeightPx = with(density) { maxBannerHeight.toPx() }

    val nestedScrollConnection = remember(density, maxBannerHeightPx, minBannerHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f) return Offset.Zero
                val previous = bannerHeightPx
                bannerHeightPx = (bannerHeightPx + available.y).coerceIn(minBannerHeightPx, maxBannerHeightPx)
                ratio = (bannerHeightPx - minBannerHeightPx) / (maxBannerHeightPx - minBannerHeightPx)
                return if (previous != bannerHeightPx) available.copy(x = 0f) else Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val delta = when {
                    available.y < 0f || consumed.y < 0f -> consumed.y
                    available.y > 0f -> available.y
                    else -> return Offset.Zero
                }
                val previous = bannerHeightPx
                bannerHeightPx = (bannerHeightPx + delta).coerceIn(minBannerHeightPx, maxBannerHeightPx)
                ratio = (bannerHeightPx - minBannerHeightPx) / (maxBannerHeightPx - minBannerHeightPx)
                return Offset(0f, bannerHeightPx - previous)
            }
        }
    }

    Box(modifier.nestedScroll(nestedScrollConnection)) {
        banner(
            ratio,
            Modifier.height(with(density) { bannerHeightPx.toDp() }).fillMaxWidth(),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = with(density) { bannerHeightPx.toDp() })
                .background(contentBackgroundColor)
                .padding(contentPadding),
        ) { content() }
        bannerElevatedContent(
            ratio,
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = statusBarHeight + ProfileLargePadding * ratio * 0.9f)
                .padding(end = ProfileLargePadding),
        )
    }
}

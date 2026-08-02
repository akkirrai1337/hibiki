package org.akkirrai.hibiki.shared.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.delay

private const val PLAYER_OVERLAY_ANIMATION_MS = 220
private const val PLAYER_OVERLAY_TAP_GUARD_MS = 120L
private const val PLAYER_PANEL_DISMISS_FLING_VELOCITY = 900f

@Composable
fun AppPlayerOverlayPanel(
    onDismissRequest: () -> Unit,
    widthFraction: Float,
    maxWidth: Dp,
    restingOffsetY: Dp = PlayerSettingsPanelRestingOffsetY,
    swipeToDismissEnabled: Boolean = true,
    nowMs: () -> Long,
    backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit,
    content: @Composable ((() -> Unit)) -> Unit,
) {
    val density = LocalDensity.current
    val openedAtMs = remember { nowMs() }

    var animatingIn by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val exitOffsetPx = with(density) { PlayerOverlayPanelExitOffset.toPx() }
    val dismissThresholdPx = with(density) { PlayerPanelDismissDragThreshold.toPx() }

    val scrimBaseAlpha by animateFloatAsState(
        targetValue = if (animatingIn) PlayerOverlayScrimAlpha else 0f,
        animationSpec = tween(durationMillis = PLAYER_OVERLAY_ANIMATION_MS),
        label = "playerOverlayScrimAlpha",
    )
    val panelAlpha by animateFloatAsState(
        targetValue = if (animatingIn) 1f else 0f,
        animationSpec = tween(durationMillis = PLAYER_OVERLAY_ANIMATION_MS),
        label = "playerOverlayPanelAlpha",
    )
    val panelScale by animateFloatAsState(
        targetValue = if (animatingIn) 1f else 0.96f,
        animationSpec = tween(durationMillis = PLAYER_OVERLAY_ANIMATION_MS),
        label = "playerOverlayPanelScale",
    )
    val panelBaseOffsetPx by animateFloatAsState(
        targetValue = if (animatingIn) 0f else exitOffsetPx,
        animationSpec = tween(durationMillis = PLAYER_OVERLAY_ANIMATION_MS),
        label = "playerOverlayPanelBaseOffset",
    )
    val animatedDragOffsetPx by animateFloatAsState(
        targetValue = dragOffsetPx,
        animationSpec = tween(durationMillis = if (isDragging) 0 else 160),
        label = "playerOverlayDragOffset",
    )

    val dragProgress = (animatedDragOffsetPx / dismissThresholdPx).coerceIn(0f, 1f)
    val effectiveScrimAlpha = scrimBaseAlpha * (1f - dragProgress * 0.45f)
    val effectivePanelScale = panelScale * (1f - dragProgress * 0.02f)
    val effectivePanelAlpha = panelAlpha * (1f - dragProgress * 0.12f)

    LaunchedEffect(Unit) {
        animatingIn = true
    }

    fun dismissPanel() {
        if (dismissing) return
        dismissing = true
        isDragging = false
        animatingIn = false
    }

    fun finishPanelDrag() {
        if (dismissing) return
        isDragging = false
        if (dragOffsetPx >= dismissThresholdPx) dismissPanel() else dragOffsetPx = 0f
    }

    val nestedScrollConnection = remember(dismissing, swipeToDismissEnabled) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (
                    !swipeToDismissEnabled ||
                    dismissing ||
                    source != NestedScrollSource.UserInput ||
                    available.y <= 0f
                ) return Offset.Zero

                isDragging = true
                dragOffsetPx = (dragOffsetPx + available.y).coerceAtLeast(0f)
                return Offset(x = 0f, y = available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (swipeToDismissEnabled && dragOffsetPx > 0f) {
                    if (
                        dragOffsetPx >= dismissThresholdPx ||
                        available.y >= PLAYER_PANEL_DISMISS_FLING_VELOCITY
                    ) dismissPanel() else finishPanelDrag()
                    return Velocity.Zero
                }
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(dismissing) {
        if (dismissing) {
            delay(PLAYER_OVERLAY_ANIMATION_MS.toLong())
            onDismissRequest()
        }
    }

    backHandler(!dismissing, ::dismissPanel)

    AppPlayerOverlaySurface(
        scrimAlpha = effectiveScrimAlpha,
        scrimEnabled = !dismissing,
        panelAlpha = effectivePanelAlpha,
        panelScale = effectivePanelScale,
        panelTranslationY = panelBaseOffsetPx + animatedDragOffsetPx,
        widthFraction = widthFraction,
        maxWidth = maxWidth,
        restingOffsetY = restingOffsetY,
        panelModifier = if (swipeToDismissEnabled) Modifier.nestedScroll(nestedScrollConnection) else Modifier,
        showHandle = swipeToDismissEnabled,
        onDragDelta = { deltaY ->
            if (dismissing) return@AppPlayerOverlaySurface
            isDragging = true
            dragOffsetPx = (dragOffsetPx + deltaY).coerceAtLeast(0f)
        },
        onDragEnd = {
            if (!dismissing) finishPanelDrag()
        },
        onScrimClick = {
            if (nowMs() - openedAtMs >= PLAYER_OVERLAY_TAP_GUARD_MS) dismissPanel()
        },
        onDismiss = ::dismissPanel,
        content = content,
    )
}

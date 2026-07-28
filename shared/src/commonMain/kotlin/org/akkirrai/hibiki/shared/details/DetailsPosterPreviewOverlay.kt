package org.akkirrai.hibiki.shared.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

private const val POSTER_PREVIEW_DISMISS_DELAY_MS = 180L

@Composable
fun AppDetailsPosterPreviewOverlay(
    onDismissRequest: () -> Unit,
    backHandler: @Composable (onBack: () -> Unit) -> Unit,
    posterContent: @Composable (posterModifier: Modifier) -> Unit,
    backContent: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    var isVisible by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    fun dismissAnimated() {
        if (isDismissing) return
        isDismissing = true
        isVisible = false
    }

    LaunchedEffect(isDismissing) {
        if (isDismissing) {
            delay(POSTER_PREVIEW_DISMISS_DELAY_MS)
            onDismissRequest()
        }
    }

    backHandler(::dismissAnimated)

    AppDetailsPosterPreviewAnimation(visible = isVisible) { scrimAlpha, posterAlpha, posterScale ->
        AppDetailsPosterPreviewSurface(
            scrimAlpha = scrimAlpha,
            posterAlpha = posterAlpha,
            posterScale = posterScale,
            onDismiss = ::dismissAnimated,
            posterContent = posterContent,
            backContent = { backContent(::dismissAnimated) },
        )
    }
}

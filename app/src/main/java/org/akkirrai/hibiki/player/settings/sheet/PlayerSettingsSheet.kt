package org.akkirrai.hibiki.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import org.akkirrai.hibiki.app.navigation.AppPlayerSettingsDestination as PlayerSettingsDestination

@Composable
fun AppPlayerSettingsSheet(
    destination: PlayerSettingsDestination,
    title: @Composable (PlayerSettingsDestination) -> String,
    onBack: () -> Unit,
    backContent: @Composable () -> Unit,
    content: LazyListScope.(PlayerSettingsDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = PlayerSettingsSheetBottomPadding),
    ) {
        AnimatedContent(
            targetState = destination,
            transitionSpec = { playerSettingsPageTransition() },
            label = "PlayerSettingsPage",
        ) { targetDestination ->
            Column(modifier = Modifier.fillMaxWidth()) {
                if (targetDestination != PlayerSettingsDestination.Root) {
                    PlayerSettingsHeader(
                        title = title(targetDestination),
                        showBack = true,
                        onBack = onBack,
                        backContent = backContent,
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = PlayerSettingsSheetMaxHeight),
                    contentPadding = PaddingValues(bottom = PlayerSettingsSheetListBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(PlayerSettingsSheetItemGap),
                    userScrollEnabled = true,
                ) {
                    content(targetDestination)
                }
            }
        }
    }
}

fun AnimatedContentTransitionScope<PlayerSettingsDestination>.playerSettingsPageTransition(): ContentTransform {
    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
    return (
        slideInHorizontally(animationSpec = tween(180)) { width -> direction * width / 5 } +
            fadeIn(animationSpec = tween(140))
        ).togetherWith(
            slideOutHorizontally(animationSpec = tween(180)) { width -> -direction * width / 5 } +
                fadeOut(animationSpec = tween(120))
        ).using(SizeTransform(clip = false))
}

/** Shared settings-panel shell; platform code supplies settings content/actions. */
@Composable
fun AppPlayerSettingsLayer(
    onDismissRequest: () -> Unit,
    nowMs: () -> Long,
    backHandler: @Composable (Boolean, () -> Unit) -> Unit,
    content: @Composable ((() -> Unit)) -> Unit,
) {
    // Keep the settings sheet above terminal playback states, including the
    // error surface emitted by AppPlaybackOverlayHost.
    Box(modifier = Modifier.fillMaxSize().zIndex(1f)) {
        AppPlayerOverlayPanel(
            onDismissRequest = onDismissRequest,
            widthFraction = PlayerSettingsPanelWidthFraction,
            maxWidth = PlayerSettingsPanelMaxWidth,
            restingOffsetY = PlayerSettingsPanelRestingOffsetY,
            swipeToDismissEnabled = false,
            nowMs = nowMs,
            backHandler = backHandler,
            content = content,
        )
    }
}

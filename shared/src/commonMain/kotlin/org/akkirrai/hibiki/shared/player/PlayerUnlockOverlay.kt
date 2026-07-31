package org.akkirrai.hibiki.shared.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.layout.LocalAppLayoutEnvironment

@Composable
fun AppPlayerUnlockOverlay(
    visible: Boolean,
    label: String,
    onClick: () -> Unit,
    iconContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    includeSystemBottomInset: Boolean = false,
) {
    val layoutEnvironment = LocalAppLayoutEnvironment.current
    val insetModifier = if (includeSystemBottomInset) {
        if (layoutEnvironment.isProvided) {
            Modifier.padding(bottom = layoutEnvironment.bottomSystemInset)
        } else {
            Modifier.navigationBarsPadding()
        }
    } else {
        Modifier
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.then(insetModifier),
        enter = fadeIn(animationSpec = tween(160)),
        exit = fadeOut(animationSpec = tween(120)),
    ) {
        AppPlayerUnlockButton(
            label = label,
            onClick = onClick,
            iconContent = { iconContent(Modifier.size(PlayerUnlockOverlayIconSize)) },
        )
    }
}

@Composable
fun AppPlayerUnlockOverlay(
    visible: Boolean,
    label: String,
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    includeSystemBottomInset: Boolean = false,
) {
    AppPlayerUnlockOverlay(
        visible = visible,
        label = label,
        onClick = onClick,
        modifier = modifier,
        includeSystemBottomInset = includeSystemBottomInset,
        iconContent = { iconModifier ->
            Icon(
                imageVector = Icons.Outlined.LockOpen,
                contentDescription = contentDescription,
                modifier = iconModifier,
            )
        },
    )
}

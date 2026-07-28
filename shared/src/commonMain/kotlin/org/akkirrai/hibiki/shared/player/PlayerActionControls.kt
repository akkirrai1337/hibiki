package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.akkirrai.hibiki.shared.design.component.AppFilledIconButton
import org.akkirrai.hibiki.shared.design.component.AppFilledIconButtonStyle

@Composable
fun AppPlayerActionControls(
    onScaleClick: () -> Unit,
    scaleContent: @Composable () -> Unit,
    onLockClick: () -> Unit,
    lockContentDescription: String? = null,
    lockContent: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = lockContentDescription,
            tint = Color.White,
        )
    },
    pictureInPictureEnabled: Boolean,
    onPictureInPictureClick: () -> Unit,
    pictureInPictureContent: @Composable () -> Unit,
    onSettingsClick: () -> Unit,
    settingsContentDescription: String? = null,
    settingsContent: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.Outlined.Settings,
            contentDescription = settingsContentDescription,
            tint = Color.White,
        )
    },
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(PlayerActionControlsGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppFilledIconButton(
            onClick = onScaleClick,
            modifier = Modifier.size(PlayerActionButtonSize),
            style = AppFilledIconButtonStyle.DarkOverlay,
            content = scaleContent,
        )
        AppFilledIconButton(
            onClick = onLockClick,
            modifier = Modifier.size(PlayerActionButtonSize),
            style = AppFilledIconButtonStyle.DarkOverlay,
            content = lockContent,
        )
        AppFilledIconButton(
            onClick = onPictureInPictureClick,
            enabled = pictureInPictureEnabled,
            modifier = Modifier.size(PlayerActionButtonSize),
            style = AppFilledIconButtonStyle.DarkOverlay,
            content = pictureInPictureContent,
        )
        AppFilledIconButton(
            onClick = onSettingsClick,
            modifier = Modifier.size(PlayerActionButtonSize),
            style = AppFilledIconButtonStyle.DarkOverlay,
            content = settingsContent,
        )
    }
}

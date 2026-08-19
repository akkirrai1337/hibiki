package org.akkirrai.hibiki.player

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
import androidx.compose.ui.res.painterResource
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.design.component.controls.AppFilledIconButton
import org.akkirrai.hibiki.design.component.controls.AppFilledIconButtonStyle

@Composable
fun AppPlayerActionControls(
    onScaleClick: () -> Unit,
    scaleEnabled: Boolean = true,
    scaleMode: VideoScaleMode = VideoScaleMode.FIT,
    scaleContentDescription: String? = null,
    scaleContent: @Composable () -> Unit = {
        Icon(
            painter = painterResource(
                when (scaleMode) {
                    VideoScaleMode.FIT -> R.drawable.ic_player_fit_to_screen_24
                    VideoScaleMode.CROP -> R.drawable.ic_player_settings_overscan_24
                    VideoScaleMode.STRETCH -> R.drawable.ic_player_aspect_ratio_24
                },
            ),
            contentDescription = scaleContentDescription,
            tint = Color.White,
        )
    },
    onLockClick: () -> Unit,
    lockEnabled: Boolean = true,
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
    pictureInPictureContentDescription: String? = null,
    pictureInPictureContent: @Composable () -> Unit = {
        Icon(
            painter = painterResource(R.drawable.ic_player_picture_in_picture_24),
            contentDescription = pictureInPictureContentDescription,
            tint = Color.White,
        )
    },
    onSettingsClick: () -> Unit,
    settingsEnabled: Boolean = true,
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
            enabled = scaleEnabled,
            modifier = Modifier.size(PlayerActionButtonSize),
            style = AppFilledIconButtonStyle.DarkOverlay,
            content = scaleContent,
        )
        AppFilledIconButton(
            onClick = onLockClick,
            enabled = lockEnabled,
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
            enabled = settingsEnabled,
            modifier = Modifier.size(PlayerActionButtonSize),
            style = AppFilledIconButtonStyle.DarkOverlay,
            content = settingsContent,
        )
    }
}

package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.component.AppFilledIconButton
import org.akkirrai.hibiki.shared.design.component.AppFilledIconButtonStyle

@Composable
fun AppPlayerActionControls(
    onScaleClick: () -> Unit,
    scaleContent: @Composable () -> Unit,
    onLockClick: () -> Unit,
    lockContent: @Composable () -> Unit,
    pictureInPictureEnabled: Boolean,
    onPictureInPictureClick: () -> Unit,
    pictureInPictureContent: @Composable () -> Unit,
    onSettingsClick: () -> Unit,
    settingsContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppFilledIconButton(
            onClick = onScaleClick,
            modifier = Modifier.size(46.dp),
            style = AppFilledIconButtonStyle.DarkOverlay,
            content = scaleContent,
        )
        AppFilledIconButton(
            onClick = onLockClick,
            modifier = Modifier.size(46.dp),
            style = AppFilledIconButtonStyle.DarkOverlay,
            content = lockContent,
        )
        AppFilledIconButton(
            onClick = onPictureInPictureClick,
            enabled = pictureInPictureEnabled,
            modifier = Modifier.size(46.dp),
            style = AppFilledIconButtonStyle.DarkOverlay,
            content = pictureInPictureContent,
        )
        AppFilledIconButton(
            onClick = onSettingsClick,
            modifier = Modifier.size(46.dp),
            style = AppFilledIconButtonStyle.DarkOverlay,
            content = settingsContent,
        )
    }
}

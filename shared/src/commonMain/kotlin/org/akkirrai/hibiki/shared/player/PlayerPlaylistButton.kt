package org.akkirrai.hibiki.shared.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.akkirrai.hibiki.shared.design.component.AppFilledIconButton
import org.akkirrai.hibiki.shared.design.component.AppFilledIconButtonStyle

@Composable
fun AppPlayerPlaylistButton(
    onClick: () -> Unit,
    iconContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppFilledIconButton(
        onClick = onClick,
        modifier = modifier,
        style = AppFilledIconButtonStyle.DarkOverlay,
        content = iconContent,
    )
}

@Composable
fun AppPlayerPlaylistButton(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    AppPlayerPlaylistButton(
        onClick = onClick,
        modifier = modifier,
        iconContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.PlaylistPlay,
                contentDescription = contentDescription,
                tint = Color.White,
            )
        },
    )
}

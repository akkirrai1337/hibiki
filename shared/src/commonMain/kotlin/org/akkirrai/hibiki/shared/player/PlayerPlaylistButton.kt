package org.akkirrai.hibiki.shared.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

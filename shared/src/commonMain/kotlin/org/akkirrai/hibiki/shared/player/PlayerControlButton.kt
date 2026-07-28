package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.design.component.AppFilledIconButton
import org.akkirrai.hibiki.shared.design.component.AppFilledIconButtonStyle

@Composable
fun AppPlayerControlButton(
    enabled: Boolean = true,
    onClick: () -> Unit,
    iconContent: @Composable () -> Unit,
) {
    AppFilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .padding(horizontal = PlayerControlButtonHorizontalPadding)
            .size(PlayerControlButtonSize),
        style = AppFilledIconButtonStyle.DarkOverlay,
        content = iconContent,
    )
}

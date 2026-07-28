package org.akkirrai.hibiki.shared.player

import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.design.component.AppFilledIconButton
import org.akkirrai.hibiki.shared.design.component.AppFilledIconButtonStyle

@Composable
fun AppEpisodesDownloadToggle(
    isVisible: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppFilledIconButton(
        onClick = onClick,
        modifier = modifier,
        style = if (isVisible) {
            AppFilledIconButtonStyle.PrimaryContainer
        } else {
            AppFilledIconButtonStyle.Surface
        },
    ) {
        Icon(
            imageVector = Icons.Outlined.Download,
            contentDescription = contentDescription,
        )
    }
}

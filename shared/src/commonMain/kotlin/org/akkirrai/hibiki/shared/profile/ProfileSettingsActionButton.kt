package org.akkirrai.hibiki.shared.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.design.animation.continuousRotation

@Composable
fun ProfileSettingsActionButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ProfileActionButton(
        icon = Icons.Rounded.Settings,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        iconModifier = Modifier.continuousRotation(
            durationMillis = 10_000,
            label = "settings_icon_rotation",
        ),
    )
}

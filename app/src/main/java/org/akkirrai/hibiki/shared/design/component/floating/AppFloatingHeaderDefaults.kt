package org.akkirrai.hibiki.shared.design.component.floating

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object AppFloatingHeaderDefaults {
    val ControlHeight: Dp = 48.dp
    val ControlRadius: Dp = 24.dp
    val ControlIconSize: Dp = 22.dp
    val TitleHorizontalPadding: Dp = 18.dp

    @Composable
    fun containerColor(): Color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f)
}

package org.akkirrai.hibiki.shared.design.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AppFilterExpandIcon(
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
        contentDescription = null,
        modifier = modifier,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
    )
}

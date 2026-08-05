package org.akkirrai.hibiki.shared.details.screen

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppDetailsTitleSheetDragHandle(
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = if (expanded) Icons.Rounded.Close else Icons.Rounded.KeyboardArrowUp,
        contentDescription = null,
        modifier = modifier
            .padding(8.dp)
            .size(if (expanded) 16.dp else 20.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

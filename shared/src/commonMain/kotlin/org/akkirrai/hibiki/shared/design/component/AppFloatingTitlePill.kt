package org.akkirrai.hibiki.shared.design.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun AppFloatingTitlePill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color,
) {
    AppFloatingPill(modifier = modifier, containerColor = containerColor) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 18.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

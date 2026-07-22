package org.akkirrai.hibiki.shared.profile

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle

@Composable
fun ProfileEmptyState(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodySmall) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

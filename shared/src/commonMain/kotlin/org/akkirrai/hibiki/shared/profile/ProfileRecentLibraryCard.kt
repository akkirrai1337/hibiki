package org.akkirrai.hibiki.shared.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ProfileRecentLibraryCard(
    title: String?,
    emptyText: String,
    isEmpty: Boolean,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        title?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) }
        if (isEmpty) {
            Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.CenterStart) {
                ProfileEmptyState(emptyText)
            }
        } else {
            content()
        }
    }
}

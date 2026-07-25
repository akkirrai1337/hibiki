package org.akkirrai.hibiki.shared.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LibraryStatusPosterFooter(label: String, icon: ImageVector) {
    val isLongLabel = label.length > 8
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isLongLabel) Arrangement.Center else Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isLongLabel) Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = if (isLongLabel) 12.sp else MaterialTheme.typography.bodySmall.fontSize), color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

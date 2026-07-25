package org.akkirrai.hibiki.shared.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class ProfileGenreBarItem(val label: String, val count: Int, val color: Color)

@Composable
fun ProfileGenreBars(items: List<ProfileGenreBarItem>) {
    if (items.isEmpty()) return
    Row(Modifier.height(IntrinsicSize.Max)) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
            items.forEach { item ->
                Text(item.label, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.74f), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Column(
            modifier = Modifier.fillMaxHeight().padding(start = 8.dp, end = 24.dp).widthIn(max = 250.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val highest = items.maxOf { it.count }.coerceAtLeast(1)
            items.forEach { item ->
                Box(
                    modifier = Modifier.fillMaxWidth(item.count / highest.toFloat()).weight(1f).drawBehind {
                        drawRoundRect(color = item.color, size = Size(size.width, size.height), cornerRadius = CornerRadius(size.height))
                    },
                )
            }
        }
    }
}

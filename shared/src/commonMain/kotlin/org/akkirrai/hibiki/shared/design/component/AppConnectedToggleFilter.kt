package org.akkirrai.hibiki.shared.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun <T> AppConnectedToggleFilter(
    title: String,
    entries: List<T>,
    selected: T?,
    onSelected: (T?) -> Unit,
    icon: @Composable (T) -> ImageVector,
    text: @Composable (T) -> String,
    arrowContent: @Composable (Modifier) -> Unit,
    allowClearSelection: Boolean = false,
) {
    AppCollapsibleFilterSection(
        title = title,
        onLongClick = { onSelected(null) },
        arrowContent = arrowContent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            entries.forEachIndexed { index, entry ->
                AppConnectedToggleFilterItem(
                    entry = entry,
                    checked = selected == entry,
                    isFirst = index == 0,
                    isLast = index == entries.lastIndex,
                    onSelected = onSelected,
                    allowClearSelection = allowClearSelection,
                    icon = icon,
                    text = text,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun <T> AppConnectedToggleFilterItem(
    entry: T,
    checked: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onSelected: (T?) -> Unit,
    allowClearSelection: Boolean,
    icon: @Composable (T) -> ImageVector,
    text: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    val selectedRadius = 32.dp
    val innerRadius = 4.dp
    val topStart by animateDpAsState(if (checked || isFirst) selectedRadius else innerRadius, label = "filter_top_start")
    val bottomStart by animateDpAsState(if (checked || isFirst) selectedRadius else innerRadius, label = "filter_bottom_start")
    val topEnd by animateDpAsState(if (checked || isLast) selectedRadius else innerRadius, label = "filter_top_end")
    val bottomEnd by animateDpAsState(if (checked || isLast) selectedRadius else innerRadius, label = "filter_bottom_end")
    val containerColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "filter_container",
    )
    val contentColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "filter_content",
    )
    Surface(
        onClick = { onSelected(if (allowClearSelection && checked) null else entry) },
        shape = RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart),
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon(entry),
                contentDescription = text(entry),
                modifier = Modifier
                    .graphicsLayer { alpha = 0.5f }
                    .size(width = 14.dp, height = 14.dp),
            )
            Text(
                text = text(entry),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

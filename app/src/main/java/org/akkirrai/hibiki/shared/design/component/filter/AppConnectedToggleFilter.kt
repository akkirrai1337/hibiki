package org.akkirrai.hibiki.shared.design.component.filter

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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.akkirrai.hibiki.shared.design.UiDimens

@Composable
fun <T> AppConnectedToggleFilter(
    title: String,
    entries: List<T>,
    selected: T?,
    onSelected: (T?) -> Unit,
    icon: @Composable (T) -> Painter,
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
                .padding(top = UiDimens.FilterContentTopPadding),
            horizontalArrangement = Arrangement.spacedBy(UiDimens.ConnectedFilterItemGap),
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
    icon: @Composable (T) -> Painter,
    text: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    val selectedRadius = UiDimens.ConnectedFilterSelectedCorner
    val innerRadius = UiDimens.ConnectedFilterInnerCorner
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
            modifier = Modifier.padding(
                horizontal = UiDimens.ConnectedFilterItemHorizontalPadding,
                vertical = UiDimens.ConnectedFilterItemVerticalPadding,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(UiDimens.ConnectedFilterItemGap),
        ) {
            Icon(
                painter = icon(entry),
                contentDescription = text(entry),
                modifier = Modifier
                    .graphicsLayer { alpha = 0.5f }
                    .size(UiDimens.ConnectedFilterIconSize),
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

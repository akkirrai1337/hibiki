package org.akkirrai.hibiki.shared.design.component.filter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import org.akkirrai.hibiki.shared.design.UiDimens

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCollapsibleFilterSection(
    title: String,
    onLongClick: () -> Unit,
    arrowContent: @Composable (Modifier) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var visible by rememberSaveable { mutableStateOf(true) }
    val iconRotation by animateFloatAsState(
        targetValue = if (visible) 0f else -90f,
        label = "filter_arrow",
    )
    Column(
        modifier = Modifier
            .padding(vertical = UiDimens.CollapsibleFilterVerticalPadding)
            .clip(RoundedCornerShape(UiDimens.CollapsibleFilterCorner))
            .combinedClickable(
                onClick = { visible = !visible },
                onLongClick = onLongClick,
            )
            .padding(UiDimens.CollapsibleFilterContentPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UiDimens.CollapsibleFilterHeaderGap),
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                ),
            )
            arrowContent(
                Modifier
                    .requiredSize(UiDimens.CollapsibleFilterArrowSize)
                    .graphicsLayer { rotationZ = iconRotation },
            )
        }
        AnimatedVisibility(visible = visible) {
            content()
        }
    }
}

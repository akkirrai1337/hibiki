package org.akkirrai.hibiki.shared.source

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

@Composable
fun SourceLanguageSection(
    title: String,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    trailingContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(SourceLanguageSectionCornerRadius))
                .clickable(onClick = onExpandedChange)
                .padding(
                    horizontal = SourceLanguageSectionHorizontalPadding,
                    vertical = SourceLanguageSectionVerticalPadding,
                ),
            horizontalArrangement = Arrangement.spacedBy(SourceLanguageSectionHeaderGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            trailingContent()
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
fun AppExpandableSourceLanguageSection(
    stateKey: String,
    title: String,
    trailingContent: @Composable (Modifier) -> Unit,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(stateKey) { mutableStateOf(true) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        label = "${stateKey}_arrow",
    )

    SourceLanguageSection(
        title = title,
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        trailingContent = {
            trailingContent(
                Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = arrowRotation },
            )
        },
        content = content,
    )
}

@Composable
fun <T> AppSourceLanguageContent(
    stateKey: String,
    title: String,
    items: List<T>,
    trailingContent: @Composable (Modifier) -> Unit,
    emptyContent: @Composable () -> Unit,
    isSelected: (T) -> Boolean,
    itemContent: @Composable (item: T, selected: Boolean, modifier: Modifier) -> Unit,
) {
    AppExpandableSourceLanguageSection(
        stateKey = stateKey,
        title = title,
        trailingContent = trailingContent,
    ) {
        AppSourceGrid(
            items = items,
            emptyContent = emptyContent,
            itemContent = { item, modifier ->
                itemContent(item, isSelected(item), modifier)
            },
        )
    }
}

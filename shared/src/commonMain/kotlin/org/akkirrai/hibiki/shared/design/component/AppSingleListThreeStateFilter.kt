package org.akkirrai.hibiki.shared.design.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> AppSingleListThreeStateFilter(
    title: String,
    options: List<T>,
    included: Set<String>,
    excluded: Set<String>,
    onChange: (Set<String>, Set<String>) -> Unit,
    id: (T) -> String,
    text: @Composable (T) -> String,
    optionIcon: @Composable ((T) -> ImageVector?)? = null,
    maxCollapsedItems: Int? = null,
    maxCollapsedGroups: Int? = null,
    allowExclusion: Boolean = true,
    optionSortKey: ((T) -> String)? = null,
    groupByFirstLetter: Boolean = false,
    arrowContent: @Composable (Modifier) -> Unit,
    expandIconContent: @Composable (Boolean, Modifier) -> Unit,
) {
    var showAllOptions by rememberSaveable(title) { mutableStateOf(false) }
    AppCollapsibleFilterSection(
        title = title,
        onLongClick = { onChange(emptySet(), emptySet()) },
        arrowContent = arrowContent,
    ) {
        Column(modifier = Modifier.padding(top = 16.dp)) {
            val sortedOptions = if (optionSortKey == null) {
                options
            } else {
                options.sortedBy { optionSortKey(it).lowercase() }
            }
            val selectedIds = included + excluded
            val visibleOptions = if (maxCollapsedItems != null && !showAllOptions) {
                (sortedOptions.take(maxCollapsedItems) + sortedOptions.filter { id(it) in selectedIds })
                    .distinctBy(id)
            } else {
                sortedOptions
            }
            if (groupByFirstLetter) {
                val groupedOptions = visibleOptions.groupBy { option ->
                    optionSortKey?.invoke(option)
                        ?.trim()
                        ?.firstOrNull()
                        ?.uppercase()
                        ?.takeIf(String::isNotBlank)
                        ?: "#"
                }.toSortedMap()
                val selectedGroupKeys = groupedOptions
                    .filterValues { group -> group.any { id(it) in selectedIds } }
                    .keys
                val visibleGroupKeys = if (maxCollapsedGroups != null && !showAllOptions) {
                    (groupedOptions.keys.take(maxCollapsedGroups) + selectedGroupKeys).toSet()
                } else {
                    groupedOptions.keys
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    groupedOptions
                        .filterKeys { it in visibleGroupKeys }
                        .forEach { (letter, groupOptions) ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = letter,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                )
                                AppSingleListThreeStateFlowRow(
                                    options = groupOptions,
                                    included = included,
                                    excluded = excluded,
                                    id = id,
                                    text = text,
                                    optionIcon = optionIcon,
                                    onChange = onChange,
                                    allowExclusion = allowExclusion,
                                )
                            }
                        }
                }
            } else {
                AppSingleListThreeStateFlowRow(
                    options = visibleOptions,
                    included = included,
                    excluded = excluded,
                    id = id,
                    text = text,
                    optionIcon = optionIcon,
                    onChange = onChange,
                    allowExclusion = allowExclusion,
                )
            }
            val groupCount = if (groupByFirstLetter) {
                sortedOptions.map { option ->
                    optionSortKey?.invoke(option)?.trim()?.firstOrNull()?.uppercase() ?: "#"
                }.distinct().size
            } else {
                0
            }
            if (
                (maxCollapsedItems != null && sortedOptions.size > maxCollapsedItems) ||
                    (maxCollapsedGroups != null && groupCount > maxCollapsedGroups)
            ) {
                IconButton(
                    onClick = { showAllOptions = !showAllOptions },
                    modifier = Modifier.align(Alignment.CenterHorizontally).size(28.dp),
                ) {
                    expandIconContent(showAllOptions, Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun <T> AppSingleListThreeStateFlowRow(
    options: List<T>,
    included: Set<String>,
    excluded: Set<String>,
    id: (T) -> String,
    text: @Composable (T) -> String,
    optionIcon: @Composable ((T) -> ImageVector?)?,
    onChange: (Set<String>, Set<String>) -> Unit,
    allowExclusion: Boolean,
) {
    val isDarkTheme = isSystemInDarkTheme()
    val includedColor = if (isDarkTheme) Color(0xFF80DF87) else Color(0xFF218739)
    val excludedColor = if (isDarkTheme) Color(0xFFFF9999) else Color(0xFFC62828)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val optionId = id(option)
            val isIncluded = optionId in included
            val isExcluded = allowExclusion && optionId in excluded
            val color = when {
                isIncluded -> includedColor
                isExcluded -> excludedColor
                else -> MaterialTheme.colorScheme.tertiary
            }
            val prefix = when {
                isIncluded -> "+ "
                isExcluded -> "− "
                else -> ""
            }
            AppSingleListFilterChip(
                color = color,
                icon = optionIcon?.invoke(option),
                text = prefix + text(option),
            ) {
                when {
                    isIncluded -> onChange(included - optionId, if (allowExclusion) excluded + optionId else emptySet())
                    isExcluded -> onChange(included, excluded - optionId)
                    else -> onChange(included + optionId, excluded - optionId)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppSingleListFilterChip(
    color: Color,
    icon: ImageVector?,
    text: String,
    onClick: () -> Unit,
) {
    val animatedColor by animateColorAsState(color, label = "filter_chip_color")
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = {})
            .background(animatedColor.copy(alpha = 0.2f), CircleShape)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon?.let {
            Icon(it, null, tint = animatedColor, modifier = Modifier.size(15.dp))
        }
        AnimatedContent(targetState = text, label = "filter_chip_text") { currentText ->
            Text(
                text = currentText,
                color = animatedColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

package org.akkirrai.hibiki.core.design.component.filter

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.akkirrai.hibiki.R

/** Applies the app language to known source aliases without changing their filter values. */
@Composable
fun appFilterOptionText(value: String): String {
    if (LocalConfiguration.current.locales[0]?.language != "ru") return value
    return russianFilterOptionLabels[value.trim().lowercase()] ?: value
}

private val russianFilterOptionLabels = mapOf(
    "ongoing" to "Онгоинг", "releasing" to "Онгоинг", "airing" to "Онгоинг",
    "finished" to "Завершено", "completed" to "Завершено", "released" to "Вышло",
    "announced" to "Анонс", "not yet released" to "Анонс", "not_yet_released" to "Анонс",
    "cancelled" to "Отменено", "canceled" to "Отменено", "hiatus" to "Перерыв", "paused" to "Перерыв",
    "movie" to "Фильм", "film" to "Фильм", "special" to "Спецвыпуск", "music" to "Музыка",
    "action" to "Экшен", "adventure" to "Приключения", "comedy" to "Комедия", "drama" to "Драма",
    "fantasy" to "Фэнтези", "horror" to "Ужасы", "mystery" to "Мистика", "romance" to "Романтика",
    "sci-fi" to "Научная фантастика", "science fiction" to "Научная фантастика", "slice of life" to "Повседневность",
    "sports" to "Спорт", "supernatural" to "Сверхъестественное", "thriller" to "Триллер", "psychological" to "Психология",
    "mecha" to "Меха", "school" to "Школа", "historical" to "Историческое", "military" to "Военное",
    "magic" to "Магия", "martial arts" to "Боевые искусства", "detective" to "Детектив", "isekai" to "Исекай",
    "seinen" to "Сэйнэн", "shounen" to "Сёнэн", "shoujo" to "Сёдзё", "josei" to "Дзёсэй",
    "kids" to "Детское", "parody" to "Пародия", "vampire" to "Вампиры", "demons" to "Демоны",
    "game" to "Игры", "harem" to "Гарем", "reverse harem" to "Обратный гарем", "ecchi" to "Этти",
)

@Composable
fun <T> AppConnectedToggleFilter(
    title: String,
    entries: List<T>,
    selected: T?,
    onSelected: (T?) -> Unit,
    icon: @Composable (T) -> ImageVector,
    text: @Composable (T) -> String,
) {
    AppCollapsibleFilterSection(title = title, onLongClick = { onSelected(null) }) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            entries.forEachIndexed { index, entry ->
                AppConnectedToggleFilterItem(
                    entry = entry,
                    checked = selected == entry,
                    isFirst = index == 0,
                    isLast = index == entries.lastIndex,
                    onSelected = onSelected,
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
        onClick = { onSelected(entry) },
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
                modifier = Modifier.graphicsLayer { alpha = 0.5f }.size(width = 14.dp, height = 14.dp),
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

@Composable
fun <T> AppThreeStateChipFilter(
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
) {
    var showAllOptions by rememberSaveable(title) { mutableStateOf(false) }
    AppCollapsibleFilterSection(title = title, onLongClick = { onChange(emptySet(), emptySet()) }) {
        Column(modifier = Modifier.padding(top = 16.dp)) {
            val effectiveExcluded = excluded.takeIf { allowExclusion }.orEmpty()
            val sortedOptions = optionSortKey?.let { sortKey ->
                options.sortedBy { sortKey(it).lowercase() }
            } ?: options
            val selectedIds = included + effectiveExcluded
            val visibleOptions = if (maxCollapsedItems != null && !showAllOptions) {
                (sortedOptions.take(maxCollapsedItems) + sortedOptions.filter { id(it) in selectedIds })
                    .distinctBy(id)
            } else {
                sortedOptions
            }
            AnimatedContent(targetState = visibleOptions, label = "filter_options") { displayedOptions ->
                if (groupByFirstLetter) {
                    val groups = displayedOptions
                    .groupBy { option ->
                        optionSortKey?.invoke(option)
                            ?.trim()
                            ?.firstOrNull()
                            ?.uppercase()
                            ?.takeIf(String::isNotBlank)
                            ?: "#"
                    }
                    .toList()
                    .sortedBy { (letter, _) -> letter }
                val selectedGroupKeys = groups
                    .filter { (_, options) -> options.any { id(it) in selectedIds } }
                    .map { (letter, _) -> letter }
                val visibleGroupKeys = if (maxCollapsedGroups != null && !showAllOptions) {
                    (groups.take(maxCollapsedGroups).map { (letter, _) -> letter } + selectedGroupKeys).toSet()
                } else {
                    groups.map { (letter, _) -> letter }.toSet()
                }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        groups
                            .filter { (letter, _) -> letter in visibleGroupKeys }
                            .forEach { (letter, groupOptions) ->
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = letter,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                    )
                                    AppThreeStateFilterFlowRow(
                                        options = groupOptions,
                                        included = included,
                                        excluded = effectiveExcluded,
                                        id = id,
                                        text = text,
                                        optionIcon = optionIcon,
                                        allowExclusion = allowExclusion,
                                        onChange = onChange,
                                    )
                                }
                            }
                    }
                } else {
                    AppThreeStateFilterFlowRow(
                        options = displayedOptions,
                        included = included,
                        excluded = effectiveExcluded,
                        id = id,
                        text = text,
                        optionIcon = optionIcon,
                        allowExclusion = allowExclusion,
                        onChange = onChange,
                    )
                }
            }
            val groupCount = if (groupByFirstLetter) {
                sortedOptions.map { optionSortKey?.invoke(it)?.trim()?.firstOrNull()?.uppercase() ?: "#" }.distinct().size
            } else 0
            if (
                (maxCollapsedItems != null && sortedOptions.size > maxCollapsedItems) ||
                    (maxCollapsedGroups != null && groupCount > maxCollapsedGroups)
            ) {
                IconButton(onClick = { showAllOptions = !showAllOptions }, modifier = Modifier.align(Alignment.CenterHorizontally).size(28.dp)) {
                    Icon(if (showAllOptions) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> AppThreeStateFilterFlowRow(
    options: List<T>,
    included: Set<String>,
    excluded: Set<String>,
    id: (T) -> String,
    text: @Composable (T) -> String,
    optionIcon: @Composable ((T) -> ImageVector?)?,
    allowExclusion: Boolean,
    onChange: (Set<String>, Set<String>) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val optionId = id(option)
            val includedOption = optionId in included
            val excludedOption = allowExclusion && optionId in excluded
            val color = when {
                includedOption -> Color(0xFF80DF87)
                excludedOption -> Color(0xFFFF9999)
                else -> MaterialTheme.colorScheme.tertiary
            }
            val prefix = when {
                includedOption -> "+ "
                excludedOption -> "− "
                else -> ""
            }
            AppFilterChip(color, optionIcon?.invoke(option), prefix + text(option)) {
                when {
                    includedOption -> onChange(included - optionId, if (allowExclusion) excluded + optionId else emptySet())
                    excludedOption -> onChange(included, excluded - optionId)
                    else -> onChange(included + optionId, excluded - optionId)
                }
            }
        }
    }
}

@Composable
private fun AppFilterChip(color: Color, icon: ImageVector?, text: String, onClick: () -> Unit) {
    Row(modifier = Modifier.clip(CircleShape).combinedClickable(onClick = onClick, onLongClick = {}).background(color.copy(alpha = 0.2f)).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        icon?.let { Icon(it, null, tint = color, modifier = Modifier.size(15.dp)) }
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun AppCollapsibleFilterSection(title: String, onLongClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    var visible by rememberSaveable(title) { mutableStateOf(true) }
    val iconRotation by animateFloatAsState(if (visible) 0f else -90f, label = "filter_arrow")
    Column(modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).combinedClickable(onClick = { visible = !visible }, onLongClick = onLongClick).padding(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Icon(ImageVector.vectorResource(R.drawable.animite_drop_down), null, Modifier.requiredSize(16.dp).graphicsLayer { rotationZ = iconRotation })
        }
        AnimatedVisibility(visible) { content() }
    }
}

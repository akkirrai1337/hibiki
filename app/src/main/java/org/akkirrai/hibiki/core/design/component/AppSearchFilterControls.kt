package org.akkirrai.hibiki.core.design.component

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
                val checked = selected == entry
                val selectedRadius = 32.dp
                val innerRadius = 4.dp
                val topStart by animateDpAsState(if (checked || index == 0) selectedRadius else innerRadius, label = "filter_top_start")
                val bottomStart by animateDpAsState(if (checked || index == 0) selectedRadius else innerRadius, label = "filter_bottom_start")
                val topEnd by animateDpAsState(if (checked || index == entries.lastIndex) selectedRadius else innerRadius, label = "filter_top_end")
                val bottomEnd by animateDpAsState(if (checked || index == entries.lastIndex) selectedRadius else innerRadius, label = "filter_bottom_end")
                val containerColor by animateColorAsState(if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, label = "filter_container")
                val contentColor by animateColorAsState(if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, label = "filter_content")
                Surface(
                    onClick = { onSelected(entry) },
                    shape = RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart),
                    color = containerColor,
                    contentColor = contentColor,
                    modifier = Modifier.weight(1f),
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
                        Text(text = text(entry), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
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
) {
    var showAllOptions by rememberSaveable(title) { mutableStateOf(false) }
    AppCollapsibleFilterSection(title = title, onLongClick = { onChange(emptySet(), emptySet()) }) {
        Column(modifier = Modifier.padding(top = 16.dp)) {
            val includedOptions = options.filter { id(it) in included }
            val excludedOptions = options.filter { id(it) in excluded }
            val allOptions = options.filterNot { id(it) in included || id(it) in excluded }
            val visibleAllOptions = if (maxCollapsedItems != null && !showAllOptions) allOptions.take(maxCollapsedItems) else allOptions
            AppChipFilterFlowRow(includedOptions, Color(0xFF80DF87), Icons.Rounded.AddCircleOutline, stringResource(R.string.search_filters_include), { onChange(included - id(it), excluded + id(it)) }, text, optionIcon, Modifier.padding(bottom = 8.dp))
            AppChipFilterFlowRow(excludedOptions, Color(0xFFFF9999), Icons.Rounded.Block, stringResource(R.string.search_filters_exclude), { onChange(included, excluded - id(it)) }, text, optionIcon, Modifier.padding(bottom = 8.dp))
            AppChipFilterFlowRow(visibleAllOptions, MaterialTheme.colorScheme.tertiary, Icons.Rounded.RadioButtonChecked, stringResource(R.string.search_filters_all), { onChange(included + id(it), excluded) }, text, optionIcon)
            if (maxCollapsedItems != null && allOptions.size > maxCollapsedItems) {
                IconButton(onClick = { showAllOptions = !showAllOptions }, modifier = Modifier.align(Alignment.CenterHorizontally).size(28.dp)) {
                    Icon(if (showAllOptions) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> AppChipFilterFlowRow(options: List<T>, color: Color, icon: ImageVector, title: String, onClick: (T) -> Unit, text: @Composable (T) -> String, optionIcon: @Composable ((T) -> ImageVector?)?, modifier: Modifier = Modifier) {
    AnimatedContent(targetState = options, label = "filter_chips") { current ->
        if (current.isNotEmpty()) Column {
            Row(modifier = Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(icon, null, Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                Text(title, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            FlowRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                current.forEach { option -> AppFilterChip(color, optionIcon?.invoke(option), text(option)) { onClick(option) } }
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

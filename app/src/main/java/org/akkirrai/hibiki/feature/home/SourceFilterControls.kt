package org.akkirrai.hibiki.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.akkirrai.beakokit.model.SourceFilterDef
import org.akkirrai.beakokit.model.SourceFilterType
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.design.component.filter.AppCollapsibleFilterSection
import org.akkirrai.hibiki.core.design.component.filter.AppConnectedToggleFilter
import org.akkirrai.hibiki.core.design.component.filter.AppThreeStateChipFilter
import org.akkirrai.hibiki.core.design.component.filter.appFilterOptionText

// The same limits the app's own genre filter uses in AnimeSearchFiltersSheet.
private const val COLLAPSED_CHIP_COUNT = 15
private const val COLLAPSED_GROUP_COUNT = 3

/**
 * Renders the filters a source defines for itself, with the same chips as the app's own type, genre
 * and status filters. Values are kept as the strings described on [SourceFilterType], keyed by
 * [SourceFilterDef.key], and only differences from a filter default are stored, so an untouched sheet
 * produces an empty map (which means "no filters").
 */
@Composable
fun SourceFilterControls(
    filters: List<SourceFilterDef>,
    values: Map<String, String>,
    onValuesChange: (Map<String, String>) -> Unit,
) {
    filters.filterNot(::isYearFilter).forEach { def -> SourceFilter(def, values, onValuesChange) }
}

@Composable
private fun SourceFilter(
    def: SourceFilterDef,
    values: Map<String, String>,
    onValuesChange: (Map<String, String>) -> Unit,
) {
    fun current() = values[def.key] ?: def.defaultValue
    fun set(value: String) =
        onValuesChange(if (value == def.defaultValue) values - def.key else values + (def.key to value))

    when (def.type) {
        SourceFilterType.HEADER -> Text(
            text = def.title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 8.dp),
        )

        SourceFilterType.SEPARATOR -> HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )

        SourceFilterType.SELECT -> {
            val selected = current().toIntOrNull()
            val defaultIndex = def.defaultValue.toIntOrNull()
            // A short list of seasons or media types is drawn as the app's own type filter: connected
            // buttons with icons. The "any" option is not a button, it is the state with none pressed.
            val iconIndices = def.options.indices.filter { it != defaultIndex }
            if (iconIndices.size in 2..6 && iconIndices.all { optionIconRes(def.options[it]) != null }) {
                AppConnectedToggleFilter(
                    title = def.title,
                    entries = iconIndices,
                    selected = selected?.takeIf { it != defaultIndex },
                    onSelected = { picked -> set((picked ?: defaultIndex ?: 0).toString()) },
                    icon = { ImageVector.vectorResource(optionIconRes(def.options[it])!!) },
                    text = { appFilterOptionText(def.options[it]) },
                )
                return
            }
            AppThreeStateChipFilter(
                title = def.title,
                options = def.options.indices.toList(),
                included = setOfNotNull(selected?.toString()),
                excluded = emptySet(),
                // A select always holds a value, so tapping the chosen option again changes nothing.
                onChange = { included, _ ->
                    included.firstOrNull { it != selected?.toString() }?.let(::set)
                },
                id = { it.toString() },
                text = { appFilterOptionText(def.options[it]) },
                allowExclusion = false,
                optionSortKey = def.options.sortKeyOrNull(),
            )
        }

        SourceFilterType.SORT -> {
            val selectedIndex = current().substringBefore(':').toIntOrNull()
            val ascending = current().substringAfter(':', "1") == "1"
            AppThreeStateChipFilter(
                title = def.title,
                options = def.options.indices.toList(),
                included = setOfNotNull(selectedIndex?.toString()),
                excluded = emptySet(),
                onChange = { included, _ ->
                    val picked = included.firstOrNull { it != selectedIndex?.toString() }
                    when {
                        picked != null -> set("$picked:1")
                        // Tapping the chosen option flips its direction, like the sort row in Aniyomi.
                        selectedIndex != null -> set("$selectedIndex:${if (ascending) 0 else 1}")
                    }
                },
                id = { it.toString() },
                text = { index ->
                    if (index == selectedIndex) "${def.options[index]} ${if (ascending) "↑" else "↓"}" else def.options[index]
                },
                allowExclusion = false,
                optionSortKey = def.options.sortKeyOrNull(),
            )
        }

        SourceFilterType.CHECKBOX -> Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(def.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Switch(checked = current().toBoolean(), onCheckedChange = { set(it.toString()) })
        }

        SourceFilterType.TRISTATE -> AppThreeStateChipFilter(
            title = def.title,
            options = listOf(def),
            included = if (current() == "1") setOf(def.key) else emptySet(),
            excluded = if (current() == "2") setOf(def.key) else emptySet(),
            onChange = { included, excluded ->
                set(
                    when {
                        def.key in included -> "1"
                        def.key in excluded -> "2"
                        else -> "0"
                    },
                )
            },
            id = { it.key },
            text = { it.title },
        )

        SourceFilterType.TEXT -> OutlinedTextField(
            value = current(),
            onValueChange = ::set,
            label = { Text(def.title) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        )

        SourceFilterType.GROUP -> {
            val compact = def.children.isNotEmpty() && def.children.all {
                it.type == SourceFilterType.CHECKBOX || it.type == SourceFilterType.TRISTATE
            }
            if (compact) {
                GroupedChipFilter(def, values, onValuesChange)
            } else {
                AppCollapsibleFilterSection(title = def.title, onLongClick = {
                    onValuesChange(values - def.children.flatMap(::descendantKeys).toSet())
                }) {
                    Column { SourceFilterControls(def.children, values, onValuesChange) }
                }
            }
        }
    }
}

/** A group of on/off or include/exclude toggles - the shape of a genre list. */
@Composable
private fun GroupedChipFilter(
    def: SourceFilterDef,
    values: Map<String, String>,
    onValuesChange: (Map<String, String>) -> Unit,
) {
    fun stateOf(child: SourceFilterDef): String = values[child.key] ?: child.defaultValue
    val included = def.children.filter { child ->
        when (child.type) {
            SourceFilterType.CHECKBOX -> stateOf(child).toBoolean()
            else -> stateOf(child) == "1"
        }
    }.mapTo(mutableSetOf(), SourceFilterDef::key)
    val excluded = def.children
        .filter { it.type == SourceFilterType.TRISTATE && stateOf(it) == "2" }
        .mapTo(mutableSetOf(), SourceFilterDef::key)
    val long = def.children.size >= SORTED_OPTION_MINIMUM

    AppThreeStateChipFilter(
        title = def.title,
        options = def.children,
        included = included,
        excluded = excluded,
        onChange = { newIncluded, newExcluded ->
            var next = values
            def.children.forEach { child ->
                val state = when (child.type) {
                    SourceFilterType.CHECKBOX -> (child.key in newIncluded).toString()
                    else -> when {
                        child.key in newIncluded -> "1"
                        child.key in newExcluded -> "2"
                        else -> "0"
                    }
                }
                next = if (state == child.defaultValue) next - child.key else next + (child.key to state)
            }
            onValuesChange(next)
        },
        id = { it.key },
        text = { appFilterOptionText(it.title) },
        maxCollapsedItems = if (long) COLLAPSED_CHIP_COUNT else null,
        maxCollapsedGroups = if (long) COLLAPSED_GROUP_COUNT else null,
        allowExclusion = def.children.any { it.type == SourceFilterType.TRISTATE },
        optionSortKey = if (long) ({ it.title }) else null,
        groupByFirstLetter = long,
    )
}

/**
 * Alphabetical order for a very long list of options, where it helps to find one; anything shorter keeps
 * the order the source gave it, as do lists led by numbers (years, ratings). Options are keyed by
 * index, so reordering only changes how they are shown.
 */
private fun List<String>.sortKeyOrNull(): ((Int) -> String)? =
    if (size >= SORTED_OPTION_MINIMUM && none { it.firstOrNull()?.isDigit() == true }) ({ index -> this[index] }) else null

private const val SORTED_OPTION_MINIMUM = 50

/** The year filter is the app's own range slider; a source's separate year filter would only duplicate it. */
private fun isYearFilter(def: SourceFilterDef): Boolean =
    def.type != SourceFilterType.GROUP && def.title.trim().lowercase() in YEAR_FILTER_TITLES

private val YEAR_FILTER_TITLES = setOf("year", "years", "release year", "year of release", "год", "рік", "год выпуска", "рік випуску")

/** Icons the app already has for seasons and media types, matched on the option's name. */
private fun optionIconRes(option: String): Int? = when (option.trim().lowercase()) {
    "winter" -> R.drawable.animite_winter
    "spring" -> R.drawable.animite_spring
    "summer" -> R.drawable.animite_summer
    "fall", "autumn" -> R.drawable.animite_fall
    "tv" -> R.drawable.animite_tv
    "ona" -> R.drawable.animite_ona
    "ova" -> R.drawable.animite_ova
    "movie", "film" -> R.drawable.animite_movie
    "special" -> R.drawable.animite_special
    "tv short" -> R.drawable.animite_tv_short
    "music" -> R.drawable.animite_music
    "one shot", "one-shot" -> R.drawable.animite_one_shot
    else -> null
}

private fun descendantKeys(def: SourceFilterDef): List<String> =
    listOf(def.key) + def.children.flatMap(::descendantKeys)

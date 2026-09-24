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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt
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
    inlineYear: (@Composable () -> Unit)? = null,
) {
    val ordered = filters.filterNot(::isYearFilter).inDisplayOrder()
    // The app's year slider follows sort, season and genre, and comes before status, language and type.
    val yearIndex = ordered.indexOfFirst { filterRank(it) > 2 }.let { if (it == -1) ordered.size else it }
    ordered.forEachIndexed { index, def ->
        if (index == yearIndex) inlineYear?.invoke()
        SourceFilter(def, values, onValuesChange)
    }
    if (yearIndex == ordered.size) inlineYear?.invoke()
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
            if (iconIndices.size in 2..6 && iconIndices.all { optionIcon(def.options[it]) != null }) {
                AppConnectedToggleFilter(
                    title = def.title,
                    entries = iconIndices,
                    selected = selected?.takeIf { it != defaultIndex },
                    onSelected = { picked -> set((picked ?: defaultIndex ?: 0).toString()) },
                    icon = { optionIcon(def.options[it])!!.asVector() },
                    text = { appFilterOptionText(def.options[it]) },
                )
                return
            }
            // A placeholder default ("<select>", "Any") is the state with nothing chosen, not a chip.
            val placeholder = defaultIndex?.takeIf { isPlaceholderOption(def.options.getOrNull(it)) }
            val sortKey = def.options.sortKeyOrNull()
            AppThreeStateChipFilter(
                title = def.title,
                options = def.options.indices.filter { it != placeholder },
                included = setOfNotNull(selected?.takeIf { it != placeholder }?.toString()),
                excluded = emptySet(),
                onChange = { included, _ ->
                    val picked = included.firstOrNull { it != selected?.toString() }
                    when {
                        picked != null -> set(picked)
                        // Tapping the chosen option again clears it when there is a placeholder to fall back to.
                        placeholder != null -> set(placeholder.toString())
                    }
                },
                id = { it.toString() },
                text = { appFilterOptionText(def.options[it]) },
                optionIcon = chipIconsFor(def).let { iconFor -> if (iconFor == null) null else ({ index -> iconFor(def.options[index]) }) },
                // A long list (genres, tags) is lettered and collapsed like the app's own genre filter.
                maxCollapsedItems = if (sortKey != null) COLLAPSED_CHIP_COUNT else null,
                maxCollapsedGroups = if (sortKey != null) COLLAPSED_GROUP_COUNT else null,
                allowExclusion = false,
                optionSortKey = sortKey,
                groupByFirstLetter = sortKey != null,
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
                optionIcon = chipIconsFor(def).let { iconFor -> if (iconFor == null) null else ({ index -> iconFor(def.options[index]) }) },
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
    if (isAgeRatingGroup(def)) {
        AgeRatingSlider(def, values, onValuesChange)
        return
    }
    if (def.children.size in 2..6 && def.children.all { optionIcon(it.title) != null }) {
        // Seasons, types, statuses and sub/dub are drawn like the app's own type filter: connected icon
        // buttons, one pressed at a time. Tapping the pressed one clears it.
        val pressed = def.children.firstOrNull { it.key in included }
        AppConnectedToggleFilter(
            title = def.title,
            entries = def.children,
            selected = pressed,
            onSelected = { picked ->
                var next = values - def.children.map(SourceFilterDef::key).toSet()
                if (picked != null && picked.key != pressed?.key) {
                    val on = if (picked.type == SourceFilterType.CHECKBOX) "true" else "1"
                    if (on != picked.defaultValue) next = next + (picked.key to on)
                }
                onValuesChange(next)
            },
            icon = { optionIcon(it.title)!!.asVector() },
            text = { appFilterOptionText(it.title) },
        )
        return
    }

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
        optionIcon = chipIconsFor(def).let { iconFor -> if (iconFor == null) null else ({ child -> iconFor(child.title) }) },
        maxCollapsedItems = if (long) COLLAPSED_CHIP_COUNT else null,
        maxCollapsedGroups = if (long) COLLAPSED_GROUP_COUNT else null,
        allowExclusion = def.children.any { it.type == SourceFilterType.TRISTATE },
        optionSortKey = if (long) ({ it.title }) else null,
        groupByFirstLetter = long,
    )
}

/**
 * Alphabetical order for a very long list of options, where it helps to find one; anything shorter keeps
 * the order the source gave it, as do lists mostly of numbers (years, ratings). Options are keyed by
 * index, so reordering only changes how they are shown.
 */
private fun List<String>.sortKeyOrNull(): ((Int) -> String)? =
    if (size >= SORTED_OPTION_MINIMUM && count { it.firstOrNull()?.isDigit() == true } < size / 2) ({ index -> this[index] }) else null

private fun isPlaceholderOption(option: String?): Boolean {
    val t = option?.trim()?.lowercase() ?: return false
    return t.isEmpty() || (t.startsWith("<") && t.endsWith(">")) || t in setOf("any", "all", "none", "-", "--")
}

private const val SORTED_OPTION_MINIMUM = 50

/** The year filter is the app's own range slider; a source's separate year filter would only duplicate it. */
private fun isYearFilter(def: SourceFilterDef): Boolean =
    def.title.trim().lowercase() in YEAR_FILTER_TITLES

private val YEAR_FILTER_TITLES = setOf("year", "years", "release year", "year of release", "год", "рік", "год выпуска", "рік випуску")

/**
 * Icons for options that name a season, a release status or a sub/dub language, matched
 * on the option's name. A drawable id or a vector; null when the name is not one of those.
 */
private fun optionIcon(option: String): Any? = when (option.trim().lowercase()) {
    "winter" -> R.drawable.animite_winter
    "spring" -> R.drawable.animite_spring
    "summer" -> R.drawable.animite_summer
    "fall", "autumn" -> R.drawable.animite_fall
    "finished airing", "finished", "completed", "ended" -> R.drawable.animite_finished
    "currently airing", "airing", "ongoing", "releasing" -> R.drawable.animite_releasing
    "not yet aired", "not yet released", "upcoming", "announced" -> R.drawable.animite_not_yet_released
    "hiatus", "on hiatus" -> R.drawable.animite_hiatus
    "cancelled", "canceled" -> R.drawable.animite_cancelled
    "sub", "subbed", "subtitles", "softsub", "hardsub" -> Icons.Outlined.Subtitles
    "dub", "dubbed" -> Icons.Outlined.RecordVoiceOver
    "raw" -> Icons.Outlined.Videocam
    else -> null
}

/** Type names, as the small icon each one gets on its chip. */
private fun typeChipIcon(option: String): ImageVector? = when (option.trim().lowercase()) {
    "movie", "film" -> Icons.Rounded.Movie
    "music" -> Icons.Rounded.MusicNote
    "ona" -> Icons.Rounded.Public
    "ova" -> Icons.Rounded.Videocam
    "special" -> Icons.Rounded.AutoAwesome
    "tv" -> Icons.Rounded.Tv
    "tv short" -> Icons.Rounded.VideoLibrary
    else -> null
}

/** Sort orders, matched on words in their name so "Latest Updated" and "Updated" get the same icon. */
private fun sortChipIcon(option: String): ImageVector? {
    val t = option.trim().lowercase()
    return when {
        "updated" in t || "update" in t -> Icons.Rounded.Update
        "added" in t || "newest" in t || "latest" in t || "new" in t -> Icons.Rounded.NewReleases
        "score" in t || "rating" in t || "rated" in t -> Icons.Rounded.Star
        "name" in t || "title" in t || "a-z" in t || "alphab" in t -> Icons.Rounded.SortByAlpha
        "release" in t || "date" in t || "year" in t || "aired" in t -> Icons.Rounded.CalendarMonth
        "view" in t || "popular" in t || "trend" in t -> Icons.Rounded.TrendingUp
        "episode" in t -> Icons.Rounded.FormatListNumbered
        "default" in t || "relevan" in t -> Icons.Rounded.Sort
        else -> null
    }
}

private fun chipIconsFor(def: SourceFilterDef): ((String) -> ImageVector?)? {
    val title = def.title.lowercase()
    return when {
        listOf("sort", "order", "сортир", "порядок").any(title::contains) -> ::sortChipIcon
        listOf("type", "format", "тип", "формат").any(title::contains) -> ::typeChipIcon
        else -> null
    }
}

// Where each kind of filter sits in the window; anything else follows in the order the source gave it.
private fun filterRank(def: SourceFilterDef): Int {
    val t = def.title.lowercase()
    return when {
        listOf("sort", "order", "сортир", "порядок").any(t::contains) -> 0
        listOf("season", "сезон").any(t::contains) -> 1
        listOf("genre", "tag", "categor", "жанр", "теги").any(t::contains) -> 2
        listOf("status", "статус").any(t::contains) -> 3
        listOf("language", "lang", "audio", "язык", "мова").any(t::contains) -> 4
        listOf("type", "format", "тип", "формат").any(t::contains) -> 5
        else -> 6
    }
}

/** Reorders top-level filters, unless the source laid them out with headers or separators of its own. */
private fun List<SourceFilterDef>.inDisplayOrder(): List<SourceFilterDef> =
    if (any { it.type == SourceFilterType.HEADER || it.type == SourceFilterType.SEPARATOR }) this
    else sortedBy(::filterRank)

@Composable
private fun Any.asVector(): ImageVector = if (this is ImageVector) this else ImageVector.vectorResource(this as Int)

private val AGE_RATING_TITLE_HINTS = listOf("rating", "age", "рейтинг", "возраст", "вік", "рейтинг")

/** Strictness of an age-rating label, lowest first; null when the label is not a recognisable rating. */
private fun ageRatingRank(title: String): Int? {
    val t = title.lowercase().replace(" ", "")
    return when {
        "rx" in t || "hentai" in t || "nc-17" in t || "18+" in t -> 5
        "r+" in t -> 4
        "r-17" in t || "r17" in t || "17+" in t || t == "r" -> 3
        "pg-13" in t || "pg13" in t || "13+" in t -> 2
        t.startsWith("pg") -> 1
        t == "g" || t.startsWith("all") || t.startsWith("g-") || "0+" in t || "everyone" in t -> 0
        else -> null
    }
}

private fun isAgeRatingGroup(def: SourceFilterDef): Boolean =
    def.children.size >= 3 &&
        AGE_RATING_TITLE_HINTS.any { def.title.lowercase().contains(it) } &&
        def.children.all {
            (it.type == SourceFilterType.CHECKBOX || it.type == SourceFilterType.TRISTATE) && ageRatingRank(it.title) != null
        }

/**
 * Age ratings are ordered, so they are picked with a slider: everything up to the chosen rating is
 * included. The far left is "any rating" and leaves the filter unset.
 */
@Composable
private fun AgeRatingSlider(
    def: SourceFilterDef,
    values: Map<String, String>,
    onValuesChange: (Map<String, String>) -> Unit,
) {
    val ordered = def.children.sortedBy { ageRatingRank(it.title) }
    fun on(child: SourceFilterDef) = if (child.type == SourceFilterType.CHECKBOX) "true" else "1"
    fun isOn(child: SourceFilterDef) = (values[child.key] ?: child.defaultValue) == on(child)
    val level = ordered.indexOfLast(::isOn) + 1
    var sliderValue by remember(level) { mutableStateOf(level.toFloat()) }
    val shownLevel = sliderValue.roundToInt().coerceIn(0, ordered.size)

    AppCollapsibleFilterSection(title = def.title, onLongClick = {
        onValuesChange(values - def.children.map(SourceFilterDef::key).toSet())
    }) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(
                text = if (shownLevel == 0) {
                    stringResource(R.string.filter_rating_any)
                } else {
                    stringResource(R.string.filter_rating_up_to, ordered[shownLevel - 1].title)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = {
                    val chosen = sliderValue.roundToInt().coerceIn(0, ordered.size)
                    sliderValue = chosen.toFloat()
                    var next = values - ordered.map(SourceFilterDef::key).toSet()
                    ordered.take(chosen).forEach { child ->
                        if (on(child) != child.defaultValue) next = next + (child.key to on(child))
                    }
                    onValuesChange(next)
                },
                valueRange = 0f..ordered.size.toFloat(),
                steps = ordered.size - 1,
            )
        }
    }
}

private fun descendantKeys(def: SourceFilterDef): List<String> =
    listOf(def.key) + def.children.flatMap(::descendantKeys)

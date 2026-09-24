package org.akkirrai.hibiki.core.source.extension

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import org.akkirrai.beakokit.model.SourceFilterDef
import org.akkirrai.beakokit.model.SourceFilterType

/**
 * Translates the filter list an Aniyomi extension builds at runtime to Hibiki's source-defined
 * filters and back. A filter is addressed by its position: `3` for the fourth top-level filter,
 * `3.5` for the sixth filter inside that group. The extension creates a fresh list for every search,
 * so values are applied onto a new list and nothing is shared between searches.
 */
internal object AniyomiFilterMapper {
    /** Describes [filters] for the filter sheet; filter kinds the sheet cannot show are left out. */
    fun describe(filters: List<AnimeFilter<*>>, prefix: String = ""): List<SourceFilterDef> =
        filters.mapIndexedNotNull { index, filter ->
            val key = "$prefix$index"
            val title = runCatching { filter.name }.getOrDefault("")
            when (filter) {
                is AnimeFilter.Header -> SourceFilterDef(key, SourceFilterType.HEADER, title)
                is AnimeFilter.Separator -> SourceFilterDef(key, SourceFilterType.SEPARATOR, title)
                is AnimeFilter.Select<*> -> SourceFilterDef(
                    key = key,
                    type = SourceFilterType.SELECT,
                    title = title,
                    options = filter.values.map { it.toString() },
                    defaultValue = filter.state.toString(),
                )
                is AnimeFilter.Text -> SourceFilterDef(key, SourceFilterType.TEXT, title, defaultValue = filter.state)
                is AnimeFilter.CheckBox -> SourceFilterDef(
                    key, SourceFilterType.CHECKBOX, title, defaultValue = filter.state.toString(),
                )
                is AnimeFilter.TriState -> SourceFilterDef(
                    key, SourceFilterType.TRISTATE, title, defaultValue = filter.state.toString(),
                )
                is AnimeFilter.Group<*> -> SourceFilterDef(
                    key = key,
                    type = SourceFilterType.GROUP,
                    title = title,
                    children = describe(filter.state.filterIsInstance<AnimeFilter<*>>(), "$key."),
                )
                is AnimeFilter.Sort -> SourceFilterDef(
                    key = key,
                    type = SourceFilterType.SORT,
                    title = title,
                    options = filter.values.toList(),
                    defaultValue = filter.state.encode(),
                )
                else -> null
            }
        }

    /** Applies [values] (see [SourceFilterType] for the encoding) onto [filters]; malformed values are ignored. */
    fun apply(filters: List<AnimeFilter<*>>, values: Map<String, String>, prefix: String = "") {
        if (values.isEmpty()) return
        filters.forEachIndexed { index, filter ->
            val key = "$prefix$index"
            val value = values[key]
            @Suppress("UNCHECKED_CAST")
            when (filter) {
                is AnimeFilter.Select<*> -> value?.toIntOrNull()
                    ?.takeIf { it in filter.values.indices }
                    ?.let { (filter as AnimeFilter<Int>).state = it }
                is AnimeFilter.Text -> value?.let { filter.state = it }
                is AnimeFilter.CheckBox -> value?.let { filter.state = it.toBoolean() }
                is AnimeFilter.TriState -> value?.toIntOrNull()
                    ?.takeIf { it in 0..2 }
                    ?.let { filter.state = it }
                is AnimeFilter.Group<*> -> apply(filter.state.filterIsInstance<AnimeFilter<*>>(), values, "$key.")
                is AnimeFilter.Sort -> value?.let { encoded ->
                    // An empty value means "no sort"; anything else must decode, or it is ignored.
                    val selection = if (encoded.isEmpty()) null else decodeSort(encoded, filter.values.size)
                    if (encoded.isEmpty() || selection != null) {
                        (filter as AnimeFilter<AnimeFilter.Sort.Selection?>).state = selection
                    }
                }
                else -> Unit
            }
        }
    }

    /** A stable text form of [values] for cache keys. */
    fun cacheKey(values: Map<String, String>): String =
        values.toSortedMap().entries.joinToString("|") { (key, value) -> "$key=$value" }

    private fun AnimeFilter.Sort.Selection?.encode(): String =
        if (this == null) "" else "$index:${if (ascending) 1 else 0}"

    private fun decodeSort(encoded: String, optionCount: Int): AnimeFilter.Sort.Selection? {
        val index = encoded.substringBefore(':').toIntOrNull()?.takeIf { it in 0 until optionCount } ?: return null
        return AnimeFilter.Sort.Selection(index, encoded.substringAfter(':', "1") == "1")
    }
}

package app.hibiki.apk

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import org.json.JSONArray
import org.json.JSONObject

/**
 * An Aniyomi extension's runtime filter list as the app's source-defined filters (SearchFilterDef in
 * shared/types.ts), and the chosen values (FilterValues) applied back. A filter is addressed by its
 * position - "3" for the fourth top-level filter, "3.5" for the sixth inside it - as the Kotlin app
 * does. The extension makes a fresh list for every search, so values always go onto a new list.
 *
 *   Select            -> select (option ids are indexes)
 *   Sort              -> select, directional ({include:[i]} ascending, {exclude:[i]} descending)
 *   Text              -> text
 *   CheckBox          -> select with the one option "on"
 *   TriState          -> tristate with the one option "on"
 *   Group of CheckBox -> multi (option ids are the children's indexes)
 *   Group of TriState -> tristate (likewise)
 *   other Group       -> its children, each on its own
 *   Header, Separator -> nothing (the filter sheet has no such rows)
 */
internal object ApkFilters {
    private const val ON = "on"

    fun describe(filters: List<AnimeFilter<*>>, prefix: String = "", titlePrefix: String = ""): JSONArray {
        val out = JSONArray()
        filters.forEachIndexed { index, filter ->
            val id = "$prefix$index"
            val title = titlePrefix + (runCatching { filter.name }.getOrNull() ?: "")
            when (filter) {
                // The first option is often a "nothing chosen" placeholder ("<select>", "Any", "Все"): the
                // app's own unset state is exactly that, so it is left out (no value = that index).
                is AnimeFilter.Select<*> -> out.put(def(id, title, "select", filter.values.mapIndexed { i, v -> option("$i", v.toString()) }
                    .filterIndexed { i, opt -> !(i == 0 && isPlaceholder(opt.getString("title"))) }))
                is AnimeFilter.Sort -> out.put(def(id, title, "select", filter.values.mapIndexed { i, v -> option("$i", v) }).put("directional", true))
                is AnimeFilter.Text -> out.put(def(id, title, "text"))
                is AnimeFilter.CheckBox -> out.put(def(id, title, "select", listOf(option(ON, title))))
                is AnimeFilter.TriState -> out.put(def(id, title, "tristate", listOf(option(ON, title))))
                is AnimeFilter.Group<*> -> {
                    val children = filter.state.filterIsInstance<AnimeFilter<*>>()
                    val options = children.mapIndexed { i, child -> option("$i", runCatching { child.name }.getOrNull() ?: "$i") }
                    when {
                        children.isNotEmpty() && children.all { it is AnimeFilter.CheckBox } -> out.put(def(id, title, "multi", options))
                        children.isNotEmpty() && children.all { it is AnimeFilter.TriState } -> out.put(def(id, title, "tristate", options))
                        else -> describe(children, "$id.", if (title.isBlank()) "" else "$title: ").let { nested ->
                            for (i in 0 until nested.length()) out.put(nested.get(i))
                        }
                    }
                }
                else -> Unit
            }
        }
        return out
    }

    /** Applies [values] (FilterValues, by the ids [describe] gave) onto [filters]; malformed values are ignored. */
    @Suppress("UNCHECKED_CAST")
    fun apply(filters: List<AnimeFilter<*>>, values: JSONObject, prefix: String = "") {
        if (values.length() == 0) return
        filters.forEachIndexed { index, filter ->
            val id = "$prefix$index"
            val value = values.opt(id)
            when (filter) {
                is AnimeFilter.Select<*> -> (value as? String)?.toIntOrNull()?.takeIf { it in filter.values.indices }
                    ?.let { (filter as AnimeFilter<Int>).state = it }
                is AnimeFilter.Sort -> (value as? JSONObject)?.let { pick ->
                    val ascending = pick.optJSONArray("include")?.optString(0)?.toIntOrNull()
                    val descending = pick.optJSONArray("exclude")?.optString(0)?.toIntOrNull()
                    val chosen = ascending ?: descending
                    if (chosen != null && chosen in filter.values.indices) {
                        (filter as AnimeFilter<AnimeFilter.Sort.Selection?>).state = AnimeFilter.Sort.Selection(chosen, ascending != null)
                    }
                }
                is AnimeFilter.Text -> (value as? String)?.let { filter.state = it }
                is AnimeFilter.CheckBox -> if (value == ON) filter.state = true
                is AnimeFilter.TriState -> (value as? JSONObject)?.let { filter.state = triState(it, ON) }
                is AnimeFilter.Group<*> -> {
                    val children = filter.state.filterIsInstance<AnimeFilter<*>>()
                    when {
                        children.isNotEmpty() && children.all { it is AnimeFilter.CheckBox } -> (value as? JSONArray)?.let { picked ->
                            val chosen = (0 until picked.length()).mapNotNull { picked.optString(it).toIntOrNull() }.toSet()
                            children.forEachIndexed { i, child -> (child as AnimeFilter.CheckBox).state = i in chosen }
                        }
                        children.isNotEmpty() && children.all { it is AnimeFilter.TriState } -> (value as? JSONObject)?.let { picked ->
                            children.forEachIndexed { i, child -> (child as AnimeFilter.TriState).state = triState(picked, "$i") }
                        }
                        else -> apply(children, values, "$id.")
                    }
                }
                else -> Unit
            }
        }
    }

    /** A stable text form of [values], for keying result pages. */
    fun cacheKey(values: JSONObject): String =
        values.keys().asSequence().sorted().joinToString("|") { "$it=${values.opt(it)}" }

    private fun triState(picked: JSONObject, option: String): Int = when {
        picked.optJSONArray("include")?.let { list -> (0 until list.length()).any { list.optString(it) == option } } == true -> AnimeFilter.TriState.STATE_INCLUDE
        picked.optJSONArray("exclude")?.let { list -> (0 until list.length()).any { list.optString(it) == option } } == true -> AnimeFilter.TriState.STATE_EXCLUDE
        else -> AnimeFilter.TriState.STATE_IGNORE
    }

    private val PLACEHOLDER = Regex(
        """^\s*(<[^>]*>|[-–—_.\s]*|any|all|none|select|choose|default|not selected|все|всё|любой|любая|любое|любые|не выбрано|выбрать|выберите|по умолчанию|усі|будь-який|оберіть)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    private fun isPlaceholder(title: String): Boolean = PLACEHOLDER.matches(title)

    private fun def(id: String, title: String, type: String, options: List<JSONObject>? = null) = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("type", type)
        if (options != null) put("options", JSONArray(options))
    }

    private fun option(id: String, title: String) = JSONObject().put("id", id).put("title", title)
}

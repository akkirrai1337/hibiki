package org.akkirrai.hibiki.core.source.extension

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import org.akkirrai.beakokit.model.SourceFilterType
import org.junit.Assert.assertEquals
import org.junit.Test

class AniyomiFilterMapperTest {
    private class Type : AnimeFilter.Select<String>("Type", arrayOf("Any", "TV", "Movie"), 0)
    private class Query : AnimeFilter.Text("Title")
    private class Airing : AnimeFilter.CheckBox("Airing")
    private class Genre(name: String) : AnimeFilter.TriState(name)
    private class Genres : AnimeFilter.Group<Genre>("Genres", listOf(Genre("Action"), Genre("Drama")))
    private class Order : AnimeFilter.Sort("Order", arrayOf("Name", "Year"), AnimeFilter.Sort.Selection(0, true))
    private class Note : AnimeFilter.Header("Note")

    private fun filters(): List<AnimeFilter<*>> = listOf(Note(), Type(), Query(), Airing(), Genres(), Order())

    @Test
    fun `describes every filter kind with its key and default`() {
        val defs = AniyomiFilterMapper.describe(filters())

        assertEquals(
            listOf(
                SourceFilterType.HEADER, SourceFilterType.SELECT, SourceFilterType.TEXT,
                SourceFilterType.CHECKBOX, SourceFilterType.GROUP, SourceFilterType.SORT,
            ),
            defs.map { it.type },
        )
        assertEquals(listOf("0", "1", "2", "3", "4", "5"), defs.map { it.key })
        assertEquals(listOf("Any", "TV", "Movie"), defs[1].options)
        assertEquals("0", defs[1].defaultValue)
        assertEquals("false", defs[3].defaultValue)
        assertEquals(listOf("4.0", "4.1"), defs[4].children.map { it.key })
        assertEquals("0:1", defs[5].defaultValue)
    }

    @Test
    fun `applies values onto a fresh list including nested and sort filters`() {
        val list = filters()
        AniyomiFilterMapper.apply(
            list,
            mapOf("1" to "2", "2" to "naruto", "3" to "true", "4.1" to "2", "5" to "1:0"),
        )

        assertEquals(2, (list[1] as Type).state)
        assertEquals("naruto", (list[2] as Query).state)
        assertEquals(true, (list[3] as Airing).state)
        val genres = (list[4] as Genres).state
        assertEquals(AnimeFilter.TriState.STATE_IGNORE, genres[0].state)
        assertEquals(AnimeFilter.TriState.STATE_EXCLUDE, genres[1].state)
        assertEquals(AnimeFilter.Sort.Selection(1, false), (list[5] as Order).state)
    }

    @Test
    fun `malformed or out of range values are ignored`() {
        val list = filters()
        AniyomiFilterMapper.apply(list, mapOf("1" to "9", "4.0" to "7", "5" to "abc", "3" to "x"))

        assertEquals(0, (list[1] as Type).state)
        assertEquals(AnimeFilter.TriState.STATE_IGNORE, (list[4] as Genres).state[0].state)
        assertEquals(AnimeFilter.Sort.Selection(0, true), (list[5] as Order).state)
        assertEquals(false, (list[3] as Airing).state)
    }

    @Test
    fun `cache key does not depend on map order`() {
        assertEquals(
            AniyomiFilterMapper.cacheKey(mapOf("b" to "1", "a" to "2")),
            AniyomiFilterMapper.cacheKey(mapOf("a" to "2", "b" to "1")),
        )
    }
}

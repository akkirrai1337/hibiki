package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.JsonNull
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.Episode

class ExternalSourceRuntimePayloadsTest {
    @Test
    fun searchPayloadPreservesAllRequestFields() {
        val payload = ExternalSourceRuntimePayloads.search(
            AnimeSearchRequest(
                query = "frieren",
                limit = 10,
                offset = 20,
                sort = AnimeSearchSort.RATING,
                typeAliases = listOf("tv"),
                statusAliases = listOf("ongoing"),
                includedGenreAliases = listOf("fantasy"),
                excludedGenreAliases = listOf("horror"),
                yearFrom = 2020,
                yearTo = 2024,
            ),
        )

        assertEquals("frieren", payload["query"]?.toString()?.trim('"'))
        assertEquals(10, payload["limit"]?.toString()?.toInt())
        assertEquals(20, payload["offset"]?.toString()?.toInt())
        assertEquals("RATING", payload["sort"]?.toString()?.trim('"'))
        assertEquals("[\"tv\"]", payload["typeAliases"].toString())
        assertEquals("[\"ongoing\"]", payload["statusAliases"].toString())
        assertEquals("[\"fantasy\"]", payload["includedGenreAliases"].toString())
        assertEquals("[\"horror\"]", payload["excludedGenreAliases"].toString())
        assertEquals(2020, payload["yearFrom"]?.toString()?.toInt())
        assertEquals(2024, payload["yearTo"]?.toString()?.toInt())
    }

    @Test
    fun searchPayloadKeepsOptionalYearsAsNull() {
        val payload = ExternalSourceRuntimePayloads.search(AnimeSearchRequest())

        assertEquals(JsonNull, payload["yearFrom"])
        assertEquals(JsonNull, payload["yearTo"])
    }

    @Test
    fun detailsPayloadContainsStableIdField() {
        assertEquals("title-1", ExternalSourceRuntimePayloads.details("title-1")["id"]?.toString()?.trim('"'))
    }

    @Test
    fun playbackPayloadsContainStableSourceIdentifiers() {
        val title = AnimeTitle(
            id = "title-1",
            russianName = null,
            englishName = "Title",
            originalName = "Title",
            japaneseName = null,
            synonyms = emptyList(),
            year = null,
            type = null,
            episodeCount = null,
            posterUrl = null,
            status = null,
            description = null,
        )
        val group = PlaybackGroup(
            id = "group-1",
            title = "Dub",
            episodes = listOf(Episode("episode-1", 1.0, "Episode 1")),
        )
        val episode = group.episodes.single()

        assertEquals("title-1", ExternalSourceRuntimePayloads.playbackGroups(title)["titleId"]?.toString()?.trim('"'))
        val playerLinks = ExternalSourceRuntimePayloads.playerLinks(title, group, episode)
        assertEquals("title-1", playerLinks["titleId"]?.toString()?.trim('"'))
        assertEquals("group-1", playerLinks["groupId"]?.toString()?.trim('"'))
        assertEquals("episode-1", playerLinks["episodeId"]?.toString()?.trim('"'))
        assertEquals("1.0", playerLinks["episodeNumber"]?.toString())
    }

    @Test
    fun `details and playback payloads reject blank identifiers`() {
        assertFailsWith<IllegalArgumentException> {
            ExternalSourceRuntimePayloads.details(" ")
        }

        val title = AnimeTitle(
            id = "title-1",
            russianName = null,
            englishName = "Title",
            originalName = "Title",
            japaneseName = null,
            synonyms = emptyList(),
            year = null,
            type = null,
            episodeCount = null,
            posterUrl = null,
            status = null,
            description = null,
        )
        val group = PlaybackGroup(
            id = "group-1",
            title = "Dub",
            episodes = listOf(Episode("episode-1", 1.0, "Episode 1")),
        )

        assertFailsWith<IllegalArgumentException> {
            ExternalSourceRuntimePayloads.playbackGroups(title.copy(id = ""))
        }
        assertFailsWith<IllegalArgumentException> {
            ExternalSourceRuntimePayloads.playerLinks(title, group.copy(id = ""), group.episodes.single())
        }
        assertFailsWith<IllegalArgumentException> {
            ExternalSourceRuntimePayloads.playerLinks(
                title,
                group,
                group.episodes.single().copy(id = ""),
            )
        }
    }
}

package org.akkirrai.beakokit.metadata

import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.RelatedAnimeTitle
import org.akkirrai.beakokit.model.TitleRating
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Mirrors the desktop client's own `anilistMetadata.test.ts`, so a rule that changes on one client
 * fails on the other until it changes there too.
 */
class AniListMetadataTest {
    private fun title(
        originalName: String = "Sousou no Frieren",
        englishName: String? = "Frieren: Beyond Journey's End",
        year: Int? = 2023,
        type: String? = "tv",
        build: AnimeTitle.() -> AnimeTitle = { this },
    ) = AnimeTitle(
        id = "anichi:1",
        originalName = originalName,
        englishName = englishName,
        year = year,
        type = type,
    ).build()

    private fun external(anilistId: Int = 154587, build: ExternalMetadata.() -> ExternalMetadata = { this }) =
        ExternalMetadata(anilistId = anilistId).build()

    @Test
    fun `turns AniList HTML into plain text`() {
        assertEquals(
            "A hero and an elf.\n\nTen years pass.",
            sanitizeAniListDescription("A hero <i>and</i> an elf.<br><br>Ten years pass."),
        )
    }

    @Test
    fun `drops spoiler blocks entirely rather than just their tags`() {
        assertEquals(
            "Safe.",
            sanitizeAniListDescription("""Safe. <span class="markdown_spoiler">The hero dies.</span>"""),
        )
    }

    @Test
    fun `drops the trailing source note`() {
        assertEquals("A synopsis.", sanitizeAniListDescription("A synopsis.\n(Source: MAL)"))
    }

    @Test
    fun `decodes entities and returns null for an empty description`() {
        assertEquals("Tom & Jerry's", sanitizeAniListDescription("Tom &amp; Jerry&#039;s"))
        assertNull(sanitizeAniListDescription("<br>"))
        assertNull(sanitizeAniListDescription(null))
    }

    @Test
    fun `collapses the season wording sites disagree about`() {
        assertEquals(
            normalizeTitleForMatch("Mushoku Tensei II - Season 2"),
            normalizeTitleForMatch("Mushoku Tensei II: 2nd Season"),
        )
    }

    @Test
    fun `keeps non-latin scripts`() {
        assertEquals("葬送のフリーレン", normalizeTitleForMatch("葬送のフリーレン"))
    }

    @Test
    fun `scores an exact name with a matching year and type at the top`() {
        val score = scoreCandidate(
            title(),
            MatchCandidate(1, listOf("Frieren: Beyond Journey's End"), year = 2023, type = "tv"),
        )
        assertEquals(1.0, score)
    }

    @Test
    fun `ignores a one-year disagreement between sites`() {
        val near = scoreCandidate(title(), MatchCandidate(1, listOf("Sousou no Frieren"), year = 2024, type = "tv"))
        val off = scoreCandidate(title(), MatchCandidate(1, listOf("Sousou no Frieren"), year = 2018, type = "tv"))
        assertEquals(1.0, near)
        assertTrue(off < near, "a four-year gap must score below a one-year one")
    }

    @Test
    fun `refuses a candidate that shares no name`() {
        assertEquals(0.0, scoreCandidate(title(), MatchCandidate(2, listOf("Bocchi the Rock!"), year = 2023, type = "tv")))
    }

    @Test
    fun `matches on a synonym when the main names differ`() {
        val dubbed = title(originalName = "Sousou no Frieren", englishName = null) {
            copy(synonyms = listOf("Frieren at the Funeral"))
        }
        assertTrue(scoreCandidate(dubbed, MatchCandidate(3, listOf("Frieren at the Funeral"), year = 2023)) > MATCH_CONFIDENCE_THRESHOLD)
    }

    @Test
    fun `derives the type from an AniList format when the candidate has none`() {
        val movie = title(originalName = "Koe no Katachi", englishName = "A Silent Voice", year = 2016, type = "movie")
        assertEquals(1.0, scoreCandidate(movie, MatchCandidate(4, listOf("A Silent Voice"), year = 2016, format = "MOVIE")))
    }

    @Test
    fun `separates a sequel from its movie by year and type`() {
        val anime = title(originalName = "Mushoku Tensei", englishName = null, year = 2023, type = "tv")
        val best = pickBestMatch(
            anime,
            listOf(
                MatchCandidate(10, listOf("Mushoku Tensei"), year = 2021, type = "tv"),
                MatchCandidate(11, listOf("Mushoku Tensei"), year = 2023, type = "tv"),
                MatchCandidate(12, listOf("Mushoku Tensei"), year = 2023, type = "movie"),
            ),
        )
        assertEquals(11, best?.anilistId)
    }

    @Test
    fun `returns nothing rather than a weak guess`() {
        assertNull(pickBestMatch(title(), listOf(MatchCandidate(20, listOf("Something Else"), year = 2023))))
    }

    @Test
    fun `keeps the source's title unchanged when there is no match`() {
        val source = title { copy(description = "Source text") }
        assertSame(source, mergeExternalMetadata(source, null))
    }

    @Test
    fun `never touches what the source knows about playback`() {
        val source = title { copy(availableEpisodeCount = 7) }
        val merged = mergeExternalMetadata(source, external { copy(episodeCount = 28) })
        assertEquals(7, merged.availableEpisodeCount)
        assertEquals(28, merged.episodeCount)
        assertEquals("anichi:1", merged.id)
    }

    @Test
    fun `keeps the source's related titles, whose ids only that source can resolve`() {
        val related = listOf(RelatedAnimeTitle(id = "anichi:sequel-1", title = "Season 2"))
        val merged = mergeExternalMetadata(title { copy(relatedAnime = related) }, external())
        assertEquals(related, merged.relatedAnime)
    }

    @Test
    fun `keeps the source's value for a field AniList left empty`() {
        val source = title {
            copy(description = "Source text", genres = listOf("Fantasy"), posterUrl = "https://source/poster.jpg")
        }
        val merged = mergeExternalMetadata(source, external { copy(description = null, genres = emptyList(), posterUrl = null) })
        assertEquals("Source text", merged.description)
        assertEquals(listOf("Fantasy"), merged.genres)
        assertEquals("https://source/poster.jpg", merged.posterUrl)
    }

    @Test
    fun `keeps a Russian name AniList cannot provide`() {
        val merged = mergeExternalMetadata(
            title { copy(russianName = "Провожающая в последний путь Фрирен") },
            external { copy(englishName = "Frieren") },
        )
        assertEquals("Провожающая в последний путь Фрирен", merged.russianName)
        assertEquals("Frieren", merged.englishName)
    }

    @Test
    fun `files the score as a ten-point rating without duplicating itself on a refetch`() {
        val source = title { copy(ratings = listOf(TitleRating(source = "Shikimori", value = 8.9))) }
        val once = mergeExternalMetadata(source, external { copy(averageScore = 92) })
        val twice = mergeExternalMetadata(once, external { copy(averageScore = 91) })
        assertEquals(
            listOf(
                TitleRating(source = ANILIST_RATING_SOURCE, value = 9.1),
                TitleRating(source = "Shikimori", value = 8.9),
            ),
            twice.ratings,
        )
    }

    @Test
    fun `converts nothing when AniList has no score`() {
        val source = title { copy(ratings = listOf(TitleRating(source = "Shikimori", value = 8.9))) }
        val merged = mergeExternalMetadata(source, external { copy(averageScore = null) })
        assertEquals(listOf(TitleRating(source = "Shikimori", value = 8.9)), merged.ratings)
    }
}

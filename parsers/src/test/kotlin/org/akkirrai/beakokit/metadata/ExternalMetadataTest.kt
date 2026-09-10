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
 * Mirrors the desktop client's own `externalMetadata.test.ts`, so a rule that changes on one client
 * fails on the other until it changes there too.
 */
class ExternalMetadataTest {
    private fun title(
        originalName: String = "Sousou no Frieren",
        englishName: String? = "Frieren: Beyond Journey's End",
        year: Int? = 2023,
        type: String? = "tv",
        build: AnimeTitle.() -> AnimeTitle = { this },
    ) = AnimeTitle(id = "anichi:1", originalName = originalName, englishName = englishName, year = year, type = type).build()

    private fun external(build: ExternalMetadata.() -> ExternalMetadata = { this }) =
        ExternalMetadata(provider = MetadataProviderId.ANILIST, externalId = 154587, anilistId = 154587).build()

    @Test
    fun `turns provider HTML into plain text`() {
        assertEquals(
            "A hero and an elf.\n\nTen years pass.",
            sanitizeDescription("A hero <i>and</i> an elf.<br><br>Ten years pass."),
        )
    }

    @Test
    fun `drops spoiler blocks entirely rather than just their tags`() {
        assertEquals("Safe.", sanitizeDescription("""Safe. <span class="markdown_spoiler">The hero dies.</span>"""))
    }

    @Test
    fun `drops the attribution tails both kinds of provider add`() {
        assertEquals("A synopsis.", sanitizeDescription("A synopsis.\n(Source: MAL)"))
        assertEquals("A synopsis.", sanitizeDescription("A synopsis.\n\n[Written by MAL Rewrite]"))
    }

    @Test
    fun `decodes entities and returns null for an empty description`() {
        assertEquals("Tom & Jerry's", sanitizeDescription("Tom &amp; Jerry&#039;s"))
        assertNull(sanitizeDescription("<br>"))
        assertNull(sanitizeDescription(null))
    }

    @Test
    fun `collapses the season wording sites disagree about`() {
        assertEquals(
            normalizeTitleForMatch("Mushoku Tensei II - Season 2"),
            normalizeTitleForMatch("Mushoku Tensei II: 2nd Season"),
        )
    }

    @Test
    fun `reads a Roman season numeral as the number it is`() {
        assertEquals("classroom of the elite 4", normalizeTitleForMatch("Classroom of the Elite IV"))
    }

    @Test
    fun `leaves a numeral that is the whole title alone`() {
        assertEquals("x", normalizeTitleForMatch("X"))
    }

    @Test
    fun `keeps non-latin scripts`() {
        assertEquals("葬送のフリーレン", normalizeTitleForMatch("葬送のフリーレン"))
    }

    @Test
    fun `offers the name as written first, then undressed spellings`() {
        assertEquals(
            listOf("Jujutsu Kaisen (TV)", "Jujutsu Kaisen"),
            searchQueriesFor(title(originalName = "Jujutsu Kaisen (TV)", englishName = null)),
        )
        assertEquals(
            listOf(
                "Re:ZERO -Starting Life in Another World- Season 3",
                "Re:ZERO Starting Life in Another World Season 3",
                "Re:ZERO Starting Life in Another World",
            ),
            searchQueriesFor(title(originalName = "Re:ZERO -Starting Life in Another World- Season 3", englishName = null)),
        )
    }

    @Test
    fun `counts two spellings of one query as one`() {
        assertEquals(listOf("Death Note"), searchQueriesFor(title(originalName = "Death Note", englishName = null)))
    }

    @Test
    fun `scores an exact name with a matching year and type at the top`() {
        assertEquals(
            1.0,
            scoreCandidate(title(), MatchCandidate(1, listOf("Frieren: Beyond Journey's End"), year = 2023, type = "tv")),
        )
    }

    @Test
    fun `ignores a one-year disagreement between sites`() {
        val near = scoreCandidate(title(), MatchCandidate(1, listOf("Sousou no Frieren"), year = 2024, type = "tv"))
        val off = scoreCandidate(title(), MatchCandidate(1, listOf("Sousou no Frieren"), year = 2018, type = "tv"))
        assertEquals(1.0, near)
        assertTrue(off < near, "a five-year gap must score below a one-year one")
    }

    @Test
    fun `matches the same season written two different ways`() {
        val fourthSeason = title(originalName = "Classroom of the Elite IV", englishName = null, year = null, type = null)
        val score = scoreCandidate(
            fourthSeason,
            MatchCandidate(5, listOf("Classroom of the Elite 4th Season: Second Year, First Semester")),
        )
        assertTrue(score > MATCH_CONFIDENCE_THRESHOLD, "same show and season, different wording: $score")
    }

    @Test
    fun `still refuses a sequel offered for its own first season`() {
        // Not zero: the name is a prefix of the sequel's, which is exactly what a first season looks
        // like next to it. What matters is that a prefix plus a three-year disagreement cannot clear
        // the bar on its own.
        val score = scoreCandidate(title(), MatchCandidate(6, listOf("Sousou no Frieren 2nd Season"), year = 2026, type = "tv"))
        assertTrue(score < MATCH_CONFIDENCE_THRESHOLD, "a sequel must not be accepted for season one: $score")
    }

    @Test
    fun `refuses a candidate that shares no name`() {
        assertEquals(0.0, scoreCandidate(title(), MatchCandidate(2, listOf("Bocchi the Rock!"), year = 2023, type = "tv")))
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
        assertEquals(11, best?.externalId)
    }

    @Test
    fun `returns nothing rather than a weak guess`() {
        assertNull(pickBestMatch(title(), listOf(MatchCandidate(20, listOf("Something Else"), year = 2023))))
    }

    @Test
    fun `asks nothing for a source that did not ask for it`() {
        assertEquals(emptyList(), metadataProviderOrder(ExternalMetadataPreferences(), "ani-liberty", false))
    }

    @Test
    fun `puts the preferred provider first and the others behind it`() {
        assertEquals(
            listOf(MetadataProviderId.ANILIST, MetadataProviderId.MAL, MetadataProviderId.KITSU),
            metadataProviderOrder(ExternalMetadataPreferences(), "anichi", true),
        )
        assertEquals(
            listOf(MetadataProviderId.KITSU, MetadataProviderId.ANILIST, MetadataProviderId.MAL),
            metadataProviderOrder(ExternalMetadataPreferences(provider = MetadataProviderId.KITSU), "anichi", true),
        )
    }

    @Test
    fun `asks only the preferred provider when fallback is off`() {
        assertEquals(
            listOf(MetadataProviderId.ANILIST),
            metadataProviderOrder(ExternalMetadataPreferences(fallbackEnabled = false), "anichi", true),
        )
    }

    @Test
    fun `follows the global switch, and a per-source override over it`() {
        assertEquals(emptyList(), metadataProviderOrder(ExternalMetadataPreferences(enabled = false), "anichi", true))
        assertTrue(
            metadataProviderOrder(
                ExternalMetadataPreferences(enabled = false, overrides = mapOf("anichi" to true)),
                "anichi",
                true,
            ).isNotEmpty(),
        )
        assertEquals(
            emptyList(),
            metadataProviderOrder(ExternalMetadataPreferences(overrides = mapOf("anichi" to false)), "anichi", true),
        )
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
        assertEquals(related, mergeExternalMetadata(title { copy(relatedAnime = related) }, external()).relatedAnime)
    }

    @Test
    fun `keeps the source's value for a field the provider left empty`() {
        val source = title {
            copy(description = "Source text", genres = listOf("Fantasy"), posterUrl = "https://source/poster.jpg")
        }
        val merged = mergeExternalMetadata(source, external { copy(description = null, genres = emptyList(), posterUrl = null) })
        assertEquals("Source text", merged.description)
        assertEquals(listOf("Fantasy"), merged.genres)
        assertEquals("https://source/poster.jpg", merged.posterUrl)
    }

    @Test
    fun `keeps a Russian name no provider can supply`() {
        val merged = mergeExternalMetadata(
            title { copy(russianName = "Провожающая в последний путь Фрирен") },
            external { copy(englishName = "Frieren") },
        )
        assertEquals("Провожающая в последний путь Фрирен", merged.russianName)
        assertEquals("Frieren", merged.englishName)
    }

    @Test
    fun `files the score under its provider without duplicating itself on a refetch`() {
        val source = title { copy(ratings = listOf(TitleRating(source = "Shikimori", value = 8.9))) }
        val once = mergeExternalMetadata(source, external { copy(score = 9.2) })
        val twice = mergeExternalMetadata(once, external { copy(score = 9.1, scoreVotes = 918_577) })
        assertEquals(
            listOf(
                TitleRating(source = "AniList", value = 9.1, votes = 918_577),
                TitleRating(source = "Shikimori", value = 8.9),
            ),
            twice.ratings,
        )
    }

    @Test
    fun `replaces another provider's rating on a switch instead of showing both`() {
        val fromAniList = mergeExternalMetadata(title(), external { copy(score = 9.2) })
        val fromMal = mergeExternalMetadata(
            fromAniList,
            ExternalMetadata(provider = MetadataProviderId.MAL, externalId = 52991, malId = 52991, score = 9.25),
        )
        assertEquals(listOf(TitleRating(source = "MAL", value = 9.25)), fromMal.ratings)
    }

    @Test
    fun `adds no rating when the provider has no score`() {
        val source = title { copy(ratings = listOf(TitleRating(source = "Shikimori", value = 8.9))) }
        assertEquals(
            listOf(TitleRating(source = "Shikimori", value = 8.9)),
            mergeExternalMetadata(source, external { copy(score = null) }).ratings,
        )
    }

    @Test
    fun `takes an age rating from a provider that has one, and keeps the source's otherwise`() {
        val source = title { copy(ageRating = "16+") }
        assertEquals("16+", mergeExternalMetadata(source, external { copy(ageRating = null) }).ageRating)
        assertEquals(
            "PG-13 - Teens 13 or older",
            mergeExternalMetadata(source, external { copy(ageRating = "PG-13 - Teens 13 or older") }).ageRating,
        )
    }
}

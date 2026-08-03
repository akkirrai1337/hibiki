package org.akkirrai.hibiki.feature.details

import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.RelatedAnime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.akkirrai.hibiki.shared.details.DetailsHeroInfo
import org.akkirrai.hibiki.shared.details.DetailsSection
import org.akkirrai.hibiki.shared.details.RelatedSection
import org.akkirrai.hibiki.shared.details.SimilarSection
import org.akkirrai.hibiki.shared.details.buildDetailsUiModel

class DetailsUiModelTest {
    private val hero = DetailsHeroInfo("TV", "2024", "12 episodes", null, "Ongoing", "Studio")

    @Test
    fun `content sections follow source capabilities and keep their order`() {
        val anime = anime(
            related = listOf(RelatedAnime("related", "Related")),
            similar = listOf(RelatedAnime("similar", "Similar")),
        )

        val model = buildDetailsUiModel(
            anime = anime,
            hero = hero,
            description = "",
            includeRelated = true,
            includeSimilar = true,
        )

        assertEquals(listOf("related", "similar"), model.sections.map(DetailsSection::key))
        assertEquals("title", (model.sections.first() as RelatedSection).items.first().id)
    }

    @Test
    fun `duplicate content is shown only in related section`() {
        val duplicate = RelatedAnime("same", "Same")
        val anime = anime(
            related = listOf(duplicate),
            similar = listOf(duplicate, RelatedAnime("similar", "Similar")),
        )

        val model = buildDetailsUiModel(
            anime = anime,
            hero = hero,
            description = "",
            includeRelated = true,
            includeSimilar = true,
        )

        assertEquals(listOf("related", "similar"), model.sections.map(DetailsSection::key))
        assertEquals(listOf("similar"), (model.sections.last() as SimilarSection).items.map { it.id })
    }

    @Test
    fun `empty content does not create empty sections`() {
        val model = buildDetailsUiModel(
            anime = anime(),
            hero = hero,
            description = "",
            includeRelated = true,
            includeSimilar = true,
        )

        assertTrue(model.sections.isEmpty())
    }

    @Test
    fun `content from unsupported source features stays hidden`() {
        val model = buildDetailsUiModel(
            anime = anime(
                related = listOf(RelatedAnime("related", "Related")),
                similar = listOf(RelatedAnime("similar", "Similar")),
            ),
            hero = hero,
            description = "",
            includeRelated = false,
            includeSimilar = false,
        )

        assertTrue(model.sections.isEmpty())
    }

    private fun anime(
        related: List<RelatedAnime> = emptyList(),
        similar: List<RelatedAnime> = emptyList(),
    ) = Anime(
        id = "title",
        title = "Title",
        subtitle = "TV · 2024",
        episodesLabel = "12 episodes",
        status = "Ongoing",
        relatedAnime = related,
        similarAnime = similar,
    )
}

package org.akkirrai.hibiki.source.stub

import org.akkirrai.beakokit.api.AnimeSource
import org.akkirrai.beakokit.api.SourceContext
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceInfo
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.CatalogCapabilities

/**
 * Fake source used only to validate the PackageManager discovery + DexClassLoader load path.
 * Returns hardcoded data; makes no network requests.
 */
class StubSource(
    @Suppress("UNUSED_PARAMETER") context: SourceContext,
) : AnimeSource {
    override val info: SourceInfo = SourceInfo(
        id = SourceId("stub-test"),
        name = "Stub Test",
        languages = setOf(SourceLanguage.ENGLISH),
        primaryLanguage = SourceLanguage.ENGLISH,
    )

    override val catalogCapabilities: CatalogCapabilities = CatalogCapabilities(
        supportedSorts = setOf(AnimeSearchSort.RELEVANCE),
        supportedFilters = emptySet(),
    )

    override suspend fun search(query: String): List<AnimeTitle> = listOf(fakeTitle())

    override suspend fun getById(id: String): AnimeTitle = fakeTitle()

    private fun fakeTitle(): AnimeTitle = AnimeTitle(
        id = "stub-1",
        russianName = null,
        englishName = "Stub Anime",
        originalName = "Stub Anime",
        japaneseName = null,
        synonyms = emptyList(),
        year = 2026,
        type = "TV",
        episodeCount = 12,
        posterUrl = null,
        status = "Ongoing",
        description = "Loaded via PackageManager discovery from a separate installed APK.",
    )
}

package org.akkirrai.hibiki.core.source.extension

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.ChapterType
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Hoster.Companion.toHosterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.api.PlaybackGroup
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.Episode
import org.junit.Assert.assertEquals
import org.junit.Test
import rx.Observable

class AniyomiAnimeSourceAdapterTest {
    private class FakeSource(private val hosters: List<Hoster>) : AnimeCatalogueSource {
        override val id = 1L
        override val name = "Fake"
        override val lang = "en"
        override val supportsLatest = false
        override fun getFilterList() = AnimeFilterList()
        override suspend fun getAnimeDetails(anime: SAnime) = anime
        override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = emptyList()
        override suspend fun getVideoList(episode: SEpisode): List<Video> = emptyList()
        override suspend fun getHosterList(episode: SEpisode): List<Hoster> = hosters
        override fun fetchPopularAnime(page: Int): Observable<AnimesPage> = Observable.empty()
        override fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage> =
            Observable.empty()
        override fun fetchLatestUpdates(page: Int): Observable<AnimesPage> = Observable.empty()
        override fun fetchAnimeDetails(anime: SAnime): Observable<SAnime> = Observable.empty()
        override fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>> = Observable.empty()
        override fun fetchVideoList(episode: SEpisode): Observable<List<Video>> = Observable.empty()
    }

    private fun video(title: String, url: String) = Video(videoUrl = url, videoTitle = title)

    private fun links(source: AnimeCatalogueSource) = runBlocking {
        val adapter = AniyomiAnimeSourceAdapter("pkg.fake", source)
        val episode = Episode(id = "e", number = 1.0, title = null)
        adapter.getPlayerLinks(
            AnimeTitle(id = "a", originalName = "A"),
            PlaybackGroup(id = "g", title = "G", episodes = listOf(episode)),
            episode,
        )
    }

    @Test
    fun `each hoster becomes its own player in the order the source lists them`() {
        val source = FakeSource(
            listOf(
                Hoster(hosterName = "HD-2", videoList = listOf(video("1080p", "https://x/a.m3u8"))),
                Hoster(hosterName = "Sub", videoList = listOf(video("720p", "https://x/b.m3u8"), video("360p", "https://x/c.m3u8"))),
            ),
        )

        val result = links(source)

        assertEquals(listOf("HD-2", "Sub", "Sub"), result.map { it.playerName })
        assertEquals(listOf("1080p", "720p", "360p"), result.map { it.quality })
    }

    @Test
    fun `a source without hosters is one player named after the source`() {
        val source = FakeSource(
            listOf(video("720p", "https://x/a.m3u8"), video("360p", "https://x/b.m3u8")).toHosterList(),
        )

        assertEquals(listOf("Fake", "Fake"), links(source).map { it.playerName })
    }

    @Test
    fun `a failing hoster does not hide the others`() {
        val failing = object : Hoster(hosterName = "Broken") {}
        val source = FakeSource(
            listOf(failing, Hoster(hosterName = "Good", videoList = listOf(video("720p", "https://x/a.m3u8")))),
        )

        assertEquals(listOf("Good"), links(source).map { it.playerName })
    }
}

class TimestampSegmentTest {
    private fun stamp(start: Double, end: Double, type: ChapterType) =
        eu.kanade.tachiyomi.animesource.model.TimeStamp(start, end, "chapter", type)

    @Test
    fun `seconds become milliseconds and chapter types map to skippable segment types`() {
        assertEquals(
            org.akkirrai.beakokit.model.VideoSegment(org.akkirrai.beakokit.model.VideoSegmentType.OPENING, 1_500, 91_250),
            toVideoSegment(stamp(1.5, 91.25, ChapterType.Opening)),
        )
        assertEquals(
            org.akkirrai.beakokit.model.VideoSegmentType.OPENING,
            toVideoSegment(stamp(0.0, 10.0, ChapterType.MixedOp))?.type,
        )
        assertEquals(
            org.akkirrai.beakokit.model.VideoSegmentType.ENDING,
            toVideoSegment(stamp(1300.0, 1400.0, ChapterType.Ending))?.type,
        )
        assertEquals(
            org.akkirrai.beakokit.model.VideoSegmentType.UNKNOWN,
            toVideoSegment(stamp(5.0, 9.0, ChapterType.Recap))?.type,
        )
    }

    @Test
    fun `malformed ranges are dropped`() {
        val other = ChapterType.Other
        assertEquals(null, toVideoSegment(stamp(10.0, 10.0, other)))
        assertEquals(null, toVideoSegment(stamp(20.0, 10.0, other)))
        assertEquals(null, toVideoSegment(stamp(-1.0, 10.0, other)))
        assertEquals(null, toVideoSegment(stamp(0.0, Double.NaN, other)))
        assertEquals(null, toVideoSegment(stamp(0.0, Double.POSITIVE_INFINITY, other)))
    }
}

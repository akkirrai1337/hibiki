package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimeRelation
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeEpisodeUpdate
import eu.kanade.tachiyomi.animesource.model.SAnimeSeasonUpdate
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.util.awaitSingle
import rx.Observable

/**
 * Aniyomi extensions-lib v14-v17 catalogue contract, plus the related-titles members of Komikku's
 * v16 fork that Yuzono extensions are built against.
 *
 * The overrides that only call super are there on purpose: extensions-lib declares these members
 * here as well, so an APK may reference them on this interface.
 */
interface AnimeCatalogueSource : AnimeSource {
    override val lang: String
    override val supportsLatest: Boolean

    override fun getFilterList(): AnimeFilterList = super.getFilterList()

    @Suppress("DEPRECATION")
    override suspend fun getPopularAnime(page: Int): AnimesPage = fetchPopularAnime(page).awaitSingle()

    @Suppress("DEPRECATION")
    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage =
        fetchSearchAnime(page, query, filters).awaitSingle()

    @Suppress("DEPRECATION")
    override suspend fun getLatestUpdates(page: Int): AnimesPage = fetchLatestUpdates(page).awaitSingle()

    override suspend fun getAnimeEpisodeUpdate(
        anime: SAnime,
        episodes: List<SEpisode>,
        fetchDetails: Boolean,
        fetchEpisodes: Boolean,
    ): SAnimeEpisodeUpdate = super.getAnimeEpisodeUpdate(anime, episodes, fetchDetails, fetchEpisodes)

    override suspend fun getAnimeSeasonUpdate(
        anime: SAnime,
        seasons: List<SAnime>,
        fetchDetails: Boolean,
        fetchSeasons: Boolean,
    ): SAnimeSeasonUpdate = super.getAnimeSeasonUpdate(anime, seasons, fetchDetails, fetchSeasons)

    override suspend fun getRelatedAnimeList(anime: SAnime): List<AnimeRelation> = super.getRelatedAnimeList(anime)

    override suspend fun getHosterList(episode: SEpisode): List<Hoster> = super.getHosterList(episode)

    override suspend fun getVideoList(hoster: Hoster): List<Video> = super.getVideoList(hoster)

    /** Komikku v16: the source can list titles related to a given one. */
    val supportsRelatedAnimes: Boolean get() = false

    /** Komikku v16: skip the search-based related titles the app would otherwise derive. */
    val disableRelatedAnimesBySearch: Boolean get() = false

    val disableRelatedAnimes: Boolean get() = false

    /** Komikku v16: extension-provided related titles; only called when [supportsRelatedAnimes]. */
    suspend fun fetchRelatedAnimeList(anime: SAnime): List<SAnime> =
        throw UnsupportedOperationException("Unsupported!")

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getPopularAnime"))
    fun fetchPopularAnime(page: Int): Observable<AnimesPage>

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getSearchAnime"))
    fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage>

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getLatestUpdates"))
    fun fetchLatestUpdates(page: Int): Observable<AnimesPage>
}

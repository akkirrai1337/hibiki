package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimeRelation
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Hoster.Companion.toHosterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeEpisodeUpdate
import eu.kanade.tachiyomi.animesource.model.SAnimeSeasonUpdate
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import rx.Observable

/**
 * Source contract of extensions-lib v14 through v17, laid out as in Aniyomi's source-api.
 *
 * v16 replaced the per-episode video list with hosters and added seasons. v17 made
 * [getAnimeEpisodeUpdate] and [getAnimeSeasonUpdate] the entry points and added
 * [getRelatedAnimeList]. Everything newer than v14 carries a default, so a source built against an
 * older lib still loads and behaves as before; the v17 defaults fall back to the older calls.
 */
interface AnimeSource {
    val id: Long
    val name: String

    val lang: String
        get() = ""

    val supportsLatest: Boolean
        get() = false

    fun getFilterList(): AnimeFilterList = AnimeFilterList()

    suspend fun getPopularAnime(page: Int): AnimesPage = throw UnsupportedOperationException()
    suspend fun getLatestUpdates(page: Int): AnimesPage = throw UnsupportedOperationException()
    suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage =
        throw UnsupportedOperationException()

    /** v17: details and episodes in one call; by default the two older calls side by side. */
    suspend fun getAnimeEpisodeUpdate(
        anime: SAnime,
        episodes: List<SEpisode>,
        fetchDetails: Boolean,
        fetchEpisodes: Boolean,
    ): SAnimeEpisodeUpdate = supervisorScope {
        @Suppress("DEPRECATION")
        val details = if (fetchDetails) async { getAnimeDetails(anime) } else null
        @Suppress("DEPRECATION")
        val list = if (fetchEpisodes) async { getEpisodeList(anime) } else null
        SAnimeEpisodeUpdate(details?.await() ?: anime, list?.await() ?: episodes)
    }

    /** v17: details and seasons in one call; by default the two older calls side by side. */
    suspend fun getAnimeSeasonUpdate(
        anime: SAnime,
        seasons: List<SAnime>,
        fetchDetails: Boolean,
        fetchSeasons: Boolean,
    ): SAnimeSeasonUpdate = supervisorScope {
        @Suppress("DEPRECATION")
        val details = if (fetchDetails) async { getAnimeDetails(anime) } else null
        @Suppress("DEPRECATION")
        val list = if (fetchSeasons) async { getSeasonList(anime) } else null
        SAnimeSeasonUpdate(details?.await() ?: anime, list?.await() ?: seasons)
    }

    /** v17: the source groups related titles itself; only called when this is true. */
    val supportsRelatedAnime: Boolean
        get() = false

    suspend fun getRelatedAnimeList(anime: SAnime): List<AnimeRelation> =
        throw UnsupportedOperationException("This source does not provide related titles")

    /** v16 hosters. A v14 source only knows the per-episode video list: one unnamed hoster. */
    suspend fun getHosterList(episode: SEpisode): List<Hoster> = getVideoList(episode).toHosterList()

    suspend fun getVideoList(hoster: Hoster): List<Video> =
        hoster.videoList ?: throw UnsupportedOperationException("This source cannot load videos for a hoster")

    @Deprecated("Use the combined suspend API instead", ReplaceWith("getAnimeSeasonUpdate"))
    suspend fun getSeasonList(anime: SAnime): List<SAnime> =
        throw UnsupportedOperationException("This source does not provide seasons")

    @Deprecated("Use the combined suspend API instead", ReplaceWith("getAnimeEpisodeUpdate"))
    suspend fun getAnimeDetails(anime: SAnime): SAnime = throw UnsupportedOperationException()

    @Deprecated("Use the combined suspend API instead", ReplaceWith("getAnimeEpisodeUpdate"))
    suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = throw UnsupportedOperationException()

    /** v14 video list. In v16 the hoster list is the entry point instead. */
    @Deprecated("Use the hoster version instead")
    suspend fun getVideoList(episode: SEpisode): List<Video> = throw UnsupportedOperationException()

    /** Komikku's extensions-lib 16 related titles, pushed group by group. */
    suspend fun getRelatedAnimeList(
        anime: SAnime,
        exceptionHandler: (Throwable) -> Unit,
        pushResults: suspend (relatedAnime: Pair<String, List<SAnime>>, completed: Boolean) -> Unit,
    ): Unit = throw UnsupportedOperationException("This source does not provide related titles")

    @Deprecated("Use the combined suspend API instead", ReplaceWith("getAnimeEpisodeUpdate"))
    fun fetchAnimeDetails(anime: SAnime): Observable<SAnime> = throw UnsupportedOperationException()

    @Deprecated("Use the combined suspend API instead", ReplaceWith("getAnimeEpisodeUpdate"))
    fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>> = throw UnsupportedOperationException()

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getVideoList"))
    fun fetchVideoList(episode: SEpisode): Observable<List<Video>> = throw UnsupportedOperationException()
}

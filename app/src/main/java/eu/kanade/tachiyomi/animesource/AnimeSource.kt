package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Hoster.Companion.toHosterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import rx.Observable

/**
 * Source contract of extensions-lib v14 through v16.
 *
 * v16 replaced the per-episode video list with hosters and added seasons and related titles. The
 * v16 members carry defaults so that a source built against v14 still loads and behaves as before.
 */
interface AnimeSource {
    val id: Long
    val name: String

    suspend fun getAnimeDetails(anime: SAnime): SAnime
    suspend fun getEpisodeList(anime: SAnime): List<SEpisode>

    /** v14 video list. In v16 the hoster list is the entry point instead. */
    suspend fun getVideoList(episode: SEpisode): List<Video>

    suspend fun getSeasonList(anime: SAnime): List<SAnime> =
        throw UnsupportedOperationException("This source does not provide seasons")

    suspend fun getHosterList(episode: SEpisode): List<Hoster> = getVideoList(episode).toHosterList()

    suspend fun getVideoList(hoster: Hoster): List<Video> =
        hoster.videoList ?: throw UnsupportedOperationException("This source cannot load videos for a hoster")

    suspend fun getRelatedAnimeList(
        anime: SAnime,
        exceptionHandler: (Throwable) -> Unit,
        pushResults: suspend (relatedAnime: Pair<String, List<SAnime>>, completed: Boolean) -> Unit,
    ): Unit = throw UnsupportedOperationException("This source does not provide related titles")

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getAnimeDetails"))
    fun fetchAnimeDetails(anime: SAnime): Observable<SAnime>

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getEpisodeList"))
    fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>>

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getVideoList"))
    fun fetchVideoList(episode: SEpisode): Observable<List<Video>>
}

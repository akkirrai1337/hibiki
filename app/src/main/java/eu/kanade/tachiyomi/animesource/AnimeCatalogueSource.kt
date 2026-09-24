package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import rx.Observable
import eu.kanade.tachiyomi.util.awaitSingle

/** Aniyomi extensions-lib v14 catalogue contract. */
interface AnimeCatalogueSource : AnimeSource {
    val lang: String
    val supportsLatest: Boolean

    @Suppress("DEPRECATION")
    suspend fun getPopularAnime(page: Int): AnimesPage = fetchPopularAnime(page).awaitSingle()

    @Suppress("DEPRECATION")
    suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage =
        fetchSearchAnime(page, query, filters).awaitSingle()

    @Suppress("DEPRECATION")
    suspend fun getLatestUpdates(page: Int): AnimesPage = fetchLatestUpdates(page).awaitSingle()

    fun getFilterList(): AnimeFilterList

    /** v16: the source can list titles related to a given one. */
    val supportsRelatedAnimes: Boolean get() = false

    /** v16: skip the search-based related titles the app would otherwise derive. */
    val disableRelatedAnimesBySearch: Boolean get() = false

    val disableRelatedAnimes: Boolean get() = false

    /** v16: extension-provided related titles; only called when [supportsRelatedAnimes]. */
    suspend fun fetchRelatedAnimeList(anime: SAnime): List<SAnime> =
        throw UnsupportedOperationException("Unsupported!")

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getPopularAnime"))
    fun fetchPopularAnime(page: Int): Observable<AnimesPage>

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getSearchAnime"))
    fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage>

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getLatestUpdates"))
    fun fetchLatestUpdates(page: Int): Observable<AnimesPage>
}

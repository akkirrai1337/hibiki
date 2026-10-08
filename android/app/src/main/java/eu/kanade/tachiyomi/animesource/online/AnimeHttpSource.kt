package eu.kanade.tachiyomi.animesource.online

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Hoster.Companion.toHosterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.util.awaitSingle
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.net.URI
import java.security.MessageDigest

/** API v14 HTTP source base. Its network surface is intentionally limited to the v14 contract. */
abstract class AnimeHttpSource : AnimeCatalogueSource {
    protected val network: NetworkHelper by lazy { NetworkHelper.instance() }

    abstract val baseUrl: String
    open val versionId: Int = 1
    override val id: Long by lazy { generateId(name, lang, versionId) }
    open val headers: Headers by lazy { headersBuilder().build() }
    open val client: OkHttpClient
        get() = network.client

    protected fun generateId(name: String, lang: String, versionId: Int): Long {
        val key = "${name.lowercase()}/$lang/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }
            .reduce(Long::or) and Long.MAX_VALUE
    }

    protected open fun headersBuilder(): Headers.Builder = Headers.Builder().apply {
        add("User-Agent", network.defaultUserAgentProvider())
    }

    override fun toString(): String = "$name (${lang.uppercase()})"

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getPopularAnime"))
    override fun fetchPopularAnime(page: Int): Observable<AnimesPage> = client
        .newCall(popularAnimeRequest(page))
        .asObservableSuccess()
        .map { response -> response.use(::popularAnimeParse) }

    protected abstract fun popularAnimeRequest(page: Int): Request
    protected abstract fun popularAnimeParse(response: Response): AnimesPage

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getSearchAnime"))
    override fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage> =
        Observable.defer {
            try {
                client.newCall(searchAnimeRequest(page, query, filters)).asObservableSuccess()
            } catch (error: NoClassDefFoundError) {
                throw RuntimeException(error)
            }
        }.map { response -> response.use(::searchAnimeParse) }

    protected abstract fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request
    protected abstract fun searchAnimeParse(response: Response): AnimesPage

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getLatestUpdates"))
    override fun fetchLatestUpdates(page: Int): Observable<AnimesPage> = client
        .newCall(latestUpdatesRequest(page))
        .asObservableSuccess()
        .map { response -> response.use(::latestUpdatesParse) }

    protected abstract fun latestUpdatesRequest(page: Int): Request
    protected abstract fun latestUpdatesParse(response: Response): AnimesPage

    @Suppress("DEPRECATION")
    override suspend fun getAnimeDetails(anime: SAnime): SAnime = fetchAnimeDetails(anime).awaitSingle()

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getAnimeDetails"))
    override fun fetchAnimeDetails(anime: SAnime): Observable<SAnime> = client
        .newCall(animeDetailsRequest(anime))
        .asObservableSuccess()
        .map { response -> response.use { animeDetailsParse(it).apply { initialized = true } } }

    open fun animeDetailsRequest(anime: SAnime): Request = GET(baseUrl + anime.url, headers)
    protected abstract fun animeDetailsParse(response: Response): SAnime

    @Suppress("DEPRECATION")
    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = fetchEpisodeList(anime).awaitSingle()

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getEpisodeList"))
    override fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>> = client
        .newCall(episodeListRequest(anime))
        .asObservableSuccess()
        .map { response -> response.use(::episodeListParse) }

    protected open fun episodeListRequest(anime: SAnime): Request = GET(baseUrl + anime.url, headers)
    protected abstract fun episodeListParse(response: Response): List<SEpisode>

    // ---- Seasons (v16) ----

    override suspend fun getSeasonList(anime: SAnime): List<SAnime> =
        seasonListParse(client.newCall(seasonListRequest(anime)).awaitSuccess())

    protected open fun seasonListRequest(anime: SAnime): Request = GET(baseUrl + anime.url, headers)
    protected open fun seasonListParse(response: Response): List<SAnime> = throw UnsupportedOperationException()

    // ---- Hosters and videos (v16) ----

    /**
     * v16 sources implement [hosterListParse]; v14 sources only know the per-episode video list,
     * which becomes a single unnamed hoster.
     */
    override suspend fun getHosterList(episode: SEpisode): List<Hoster> =
        if (implementsHosterList) {
            hosterListParse(client.newCall(hosterListRequest(episode)).awaitSuccess())
        } else {
            getVideoList(episode).toHosterList()
        }

    protected open fun hosterListRequest(episode: SEpisode): Request = GET(baseUrl + episode.url, headers)
    protected open fun hosterListParse(response: Response): List<Hoster> = throw UnsupportedOperationException()

    override suspend fun getVideoList(hoster: Hoster): List<Video> {
        hoster.videoList?.let { return it }
        val response = client.newCall(videoListRequest(hoster)).awaitSuccess()
        return videoListParse(response, hoster).sortVideos()
    }

    protected open fun videoListRequest(hoster: Hoster): Request = GET(hoster.hosterUrl, headers)
    protected open fun videoListParse(response: Response, hoster: Hoster): List<Video> =
        throw UnsupportedOperationException()

    /** Finishes a video that was listed lazily (empty URL or [Video.internalData]); null drops it. */
    open suspend fun resolveVideo(video: Video): Video? = video

    /** Extension hook to order hosters, called by the host after [getHosterList]. */
    open fun List<Hoster>.sortHosters(): List<Hoster> = this

    /** Extension hook to order videos, called by the host after listing them. */
    protected open fun List<Video>.sortVideos(): List<Video> {
        @Suppress("DEPRECATION")
        return sort()
    }

    /** Host-side entry points: the hooks above are protected, exactly as in the extensions lib. */
    fun sortedHosters(hosters: List<Hoster>): List<Hoster> = hosters.sortHosters()
    fun sortedVideos(videos: List<Video>): List<Video> = videos.sortVideos()

    // ---- v14 per-episode video list ----

    @Suppress("DEPRECATION")
    override suspend fun getVideoList(episode: SEpisode): List<Video> = fetchVideoList(episode).awaitSingle()

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getVideoList"))
    override fun fetchVideoList(episode: SEpisode): Observable<List<Video>> = client
        .newCall(videoListRequest(episode))
        .asObservableSuccess()
        .map { response -> response.use { videoListParse(it).sortVideos() } }

    protected open fun videoListRequest(episode: SEpisode): Request = GET(baseUrl + episode.url, headers)
    protected open fun videoListParse(response: Response): List<Video> = throw UnsupportedOperationException()

    @Deprecated("Use sortVideos instead")
    protected open fun List<Video>.sort(): List<Video> = this

    // ---- Lazy video URL (deprecated in v16 in favour of resolveVideo) ----

    @Suppress("DEPRECATION")
    open suspend fun getVideoUrl(video: Video): String = fetchVideoUrl(video).awaitSingle()

    @Deprecated("Use resolveVideo instead")
    open fun fetchVideoUrl(video: Video): Observable<String> = client
        .newCall(videoUrlRequest(video))
        .asObservableSuccess()
        .map { response -> response.use(::videoUrlParse) }

    @Deprecated("Use resolveVideo instead")
    protected open fun videoUrlRequest(video: Video): Request = GET(video.url, headers)

    @Deprecated("Use resolveVideo instead")
    protected open fun videoUrlParse(response: Response): String = response.request.url.toString()

    // ---- Related titles (v16) ----

    override val supportsRelatedAnimes: Boolean get() = true

    override suspend fun fetchRelatedAnimeList(anime: SAnime): List<SAnime> =
        relatedAnimeListParse(client.newCall(relatedAnimeListRequest(anime)).awaitSuccess())

    protected open fun relatedAnimeListRequest(anime: SAnime): Request = animeDetailsRequest(anime)
    protected open fun relatedAnimeListParse(response: Response): List<SAnime> = popularAnimeParse(response).animes

    private val implementsHosterList: Boolean by lazy {
        generateSequence<Class<*>>(javaClass) { it.superclass }
            .takeWhile { it != AnimeHttpSource::class.java }
            .any { type -> type.declaredMethods.any { it.name == "hosterListParse" && it.parameterCount == 1 } }
    }

    fun SEpisode.setUrlWithoutDomain(url: String) {
        this.url = url.withoutDomain()
    }

    fun SAnime.setUrlWithoutDomain(url: String) {
        this.url = url.withoutDomain()
    }

    private fun String.withoutDomain(): String = try {
        URI(this).let { uri ->
            buildString {
                append(uri.path.orEmpty())
                uri.query?.let { append("?").append(it) }
                uri.fragment?.let { append("#").append(it) }
            }
        }
    } catch (_: Exception) {
        this
    }

    open fun getAnimeUrl(anime: SAnime): String = animeDetailsRequest(anime).url.toString()
    open fun getEpisodeUrl(episode: SEpisode): String = episode.url
    open fun prepareNewEpisode(episode: SEpisode, anime: SAnime) = Unit
    override fun getFilterList(): AnimeFilterList = AnimeFilterList()
}

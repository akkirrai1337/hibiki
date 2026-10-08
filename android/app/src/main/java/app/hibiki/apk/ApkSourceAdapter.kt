package app.hibiki.apk

import android.util.Log
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.ChapterType
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.TimeStamp
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.NetworkFailureLog
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** A third-party extension failed while running, or ran out of time. */
class ApkSourceException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/**
 * One APK source as the page sees a source: the same calls a JS source answers (search, latest,
 * title, episode groups, player links, filters), answered in the shared/types.ts shapes as JSON.
 * The Aniyomi side follows the Kotlin Hibiki app's adapter: page-based catalogues served by offset,
 * the v16 hoster flow for videos, lazy videos resolved by the source, every call off the caller's
 * thread with a deadline (extensions are third-party code: they block, hang, or throw linkage errors).
 */
class ApkSourceAdapter(
    val extension: InstalledApkExtension,
    private val source: AnimeCatalogueSource,
) {
    /** "apk:<Aniyomi source id>" - the ids are already unique across extensions (Aniyomi hashes name, lang and version into them). */
    val id: String = "apk:${java.lang.Long.toUnsignedString(source.id)}"
    val name: String get() = source.name
    val lang: String get() = source.lang
    val supportsLatest: Boolean get() = source.supportsLatest
    val aniyomiSourceId: Long get() = source.id
    val catalogue: AnimeCatalogueSource get() = source
    val baseUrl: String? get() = runCatching { (source as? AnimeHttpSource)?.baseUrl }.getOrNull()?.takeIf(String::isNotBlank)

    private val pageCache = LinkedHashMap<String, CachedPages>(16, 0.75f, true)
    private val paginationMutex = Mutex()
    private val fetchTypes = ConcurrentHashMap<String, FetchType>()
    private val typeCache = ConcurrentHashMap<String, String>()
    /** Number and name of each episode listed so far: the page asks for links by episode id alone. */
    private val episodeMeta = ConcurrentHashMap<String, Pair<Double, String?>>()

    fun info(settings: JSONArray = JSONArray()): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("version", extension.versionName)
        put("lang", lang)
        put("isNsfw", extension.isNsfw)
        put("capabilities", JSONArray().apply {
            put("PLAYBACK")
            if (supportsLatest) put("LATEST_RELEASES")
        })
        put("settings", settings)
        put("website", baseUrl ?: JSONObject.NULL)
        put("packageName", extension.packageName)
    }

    /** SearchRequest -> AnimeTitle[]. No query and no filters is the source's popular list; sort "latest" its latest updates. */
    suspend fun search(request: JSONObject): JSONArray {
        val limit = request.optInt("limit", 24).coerceIn(0, MAX_PAGE_SIZE)
        if (limit == 0) return JSONArray()
        val offset = request.optInt("offset", 0).coerceAtLeast(0)
        val query = request.optString("query", "").trim()
        val filters = request.optJSONObject("filters") ?: JSONObject()
        val latest = request.optString("sort", "") == SORT_LATEST && supportsLatest && query.isEmpty() && filters.length() == 0
        val key = (if (latest) "latest" else "q:$query") + "|" + ApkFilters.cacheKey(filters)
        return guarded("search") {
            paginationMutex.withLock {
                val cached = pageCache.getOrPut(key) { CachedPages() }
                while (cached.titles.size < offset + limit && !cached.finished) {
                    val page = cached.nextPage
                    val result = when {
                        latest -> source.getLatestUpdates(page)
                        query.isEmpty() && filters.length() == 0 -> source.getPopularAnime(page)
                        else -> {
                            // A fresh list per request: the extension keeps filter state in it.
                            val list = source.getFilterList()
                            ApkFilters.apply(list, filters)
                            source.getSearchAnime(page, query, list)
                        }
                    }
                    cached.titles += result.animes.map { toTitle(it) }
                    cached.nextPage = page + 1
                    cached.finished = !result.hasNextPage || result.animes.isEmpty()
                }
                while (pageCache.size > MAX_CACHED_QUERIES) pageCache.remove(pageCache.keys.first())
                JSONArray(cached.titles.drop(offset).take(limit))
            }
        }
    }

    suspend fun latest(limit: Int): JSONArray {
        if (!supportsLatest || limit <= 0) return JSONArray()
        return guarded("latest") { JSONArray(source.getLatestUpdates(1).animes.take(limit.coerceAtMost(MAX_PAGE_SIZE)).map { toTitle(it) }) }
    }

    /** SearchFilterCatalog: the source's own filters; "Popular" / "Latest" as the catalogue's orders. */
    suspend fun filterCatalog(): JSONObject {
        val filters = guarded("filters") { ApkFilters.describe(source.getFilterList()) }
        return JSONObject().apply {
            put("sortOptions", JSONArray().apply {
                put(JSONObject().put("id", SORT_POPULAR).put("title", "Popular"))
                if (supportsLatest) put(JSONObject().put("id", SORT_LATEST).put("title", "Latest"))
            })
            put("filters", filters)
        }
    }

    suspend fun getById(titleId: String): JSONObject {
        val anime = SAnime.create().apply {
            url = titleId
            title = titleId
        }
        return guarded("details") {
            val details = source.getAnimeDetails(anime)
            fetchTypes[titleId] = details.fetch_type
            // Some extensions rewrite the URL while loading details; the title keeps the id it was asked
            // for - the catalogue, the library and downloads all know it by that one.
            toTitle(details, fallbackId = titleId).put("id", titleId)
        }
    }

    suspend fun playbackGroups(titleId: String): JSONArray {
        val anime = SAnime.create().apply {
            url = titleId
            title = titleId
        }
        // The details tell whether the title lists seasons or episodes directly.
        val fetchType = fetchTypes[titleId] ?: guarded("details") { source.getAnimeDetails(anime) }.fetch_type.also { fetchTypes[titleId] = it }
        if (fetchType == FetchType.Seasons) {
            val seasons = guarded("season list") { source.getSeasonList(anime) }
            val groups = coroutineScope {
                seasons.mapIndexed { index, season ->
                    async {
                        val episodes = toEpisodes(guarded("episode list") { source.getEpisodeList(season) })
                        if (episodes.length() == 0) return@async null
                        val title = runCatching { season.title }.getOrNull()?.takeIf(String::isNotBlank) ?: "Season ${index + 1}"
                        JSONObject().put("id", "season-${index + 1}").put("title", title).put("episodes", episodes)
                    }
                }.awaitAll().filterNotNull()
            }
            return JSONArray(groups)
        }
        val episodes = toEpisodes(guarded("episode list") { source.getEpisodeList(anime) })
        if (episodes.length() == 0) return JSONArray()
        return JSONArray().put(JSONObject().put("id", "episodes").put("title", source.name).put("episodes", episodes))
    }

    suspend fun playerLinks(episodeId: String): JSONArray {
        val (episodeNumber, episodeTitle) = episodeMeta[episodeId] ?: (0.0 to null)
        val episode = SEpisode.create().apply {
            url = episodeId
            name = episodeTitle ?: "Episode ${formatNumber(episodeNumber)}"
            episode_number = episodeNumber.toFloat()
        }
        val startedAt = System.currentTimeMillis()
        val videos = loadVideos(episode)
        val links = coroutineScope { videos.map { hosted -> async { toPlayerLink(hosted) } }.awaitAll() }.filterNotNull()
        if (links.isEmpty()) {
            // Extensions swallow their own network errors and return nothing; the client kept them.
            val cause = NetworkFailureLog.summary(startedAt)
            throw ApkSourceException("${source.name} returned no playable videos" + (cause?.let { " ($it)" } ?: ""))
        }
        return JSONArray(links)
    }

    // --- Aniyomi -> shared/types.ts ---------------------------------------------------------------

    private fun toTitle(anime: SAnime, fallbackId: String? = null): JSONObject {
        // Some extensions return a fresh SAnime from getAnimeDetails() without its lateinit URL.
        val url = runCatching { anime.url }.getOrNull()?.takeIf(String::isNotBlank) ?: fallbackId.orEmpty()
        val title = runCatching { anime.title }.getOrNull()?.takeIf(String::isNotBlank) ?: url
        val pageUrl = runCatching { (source as? AnimeHttpSource)?.getAnimeUrl(anime) }.getOrNull()?.takeIf { it.startsWith("http") }
        return JSONObject().apply {
            put("id", url)
            put("sourceId", id)
            put("originalName", title)
            // Through the app, with the source's headers and cookies (see ApkImageProxy).
            put("posterUrl", ApkImageProxy.wrap(id, anime.thumbnail_url) ?: JSONObject.NULL)
            put("pageUrl", pageUrl ?: JSONObject.NULL)
            put("status", when (anime.status) {
                SAnime.ONGOING -> "ongoing"
                SAnime.COMPLETED, SAnime.PUBLISHING_FINISHED -> "completed"
                else -> JSONObject.NULL
            })
            put("description", anime.description?.takeIf(String::isNotBlank) ?: JSONObject.NULL)
            put("genres", JSONArray(anime.getGenres().orEmpty()))
        }
    }

    private fun toEpisodes(list: List<SEpisode>): JSONArray {
        val out = JSONArray()
        list.forEachIndexed { index, episode ->
            val url = runCatching { episode.url }.getOrNull()?.takeIf(String::isNotBlank) ?: return@forEachIndexed
            val number = episode.episode_number.toDouble().takeIf { it.isFinite() && it >= 0.0 } ?: (index + 1).toDouble()
            val name = runCatching { episode.name }.getOrNull()?.takeIf(String::isNotBlank)
            episodeMeta[url] = number to name
            out.put(JSONObject().apply {
                put("id", url)
                put("number", number)
                put("title", name ?: JSONObject.NULL)
            })
        }
        return out
    }

    /** A video with the hoster it was listed under, when the source has hosters. */
    private data class HostedVideo(val video: Video, val hosterName: String?)

    private suspend fun toPlayerLink(hosted: HostedVideo): JSONObject? {
        val video = hosted.video
        val directUrl = video.videoUrl.takeIf(String::isNotBlank)
        val url = directUrl ?: video.url
        if (url.isBlank()) return null
        val headers = video.headers?.toMultimap()?.mapValues { (_, values) -> values.joinToString(", ") }.orEmpty()
        return JSONObject().apply {
            put("url", url)
            // Aniyomi video URLs are streams, not embed pages; only a page URL the source never resolved is an embed.
            put("type", if (directUrl != null) streamType(directUrl, headers) else "EMBED")
            put("quality", video.quality.takeIf(String::isNotBlank) ?: JSONObject.NULL)
            put("headers", JSONObject(headers))
            // The hoster is the server or dub the video comes from: the player the person picks. A
            // source without hosters is one player named after the source.
            put("playerName", hosted.hosterName ?: source.name)
            put("segments", JSONArray(video.timestamps.mapNotNull(::toSegment).sortedBy { it.getLong("startMs") }))
            video.audioTracks.firstOrNull()?.url?.takeIf(String::isNotBlank)?.let {
                put("audioUrl", it)
                put("audioHeaders", JSONObject(headers))
            }
            put("subtitles", JSONArray(video.subtitleTracks.filter { it.url.isNotBlank() }.map { track ->
                JSONObject().apply {
                    put("url", track.url)
                    put("label", track.lang.takeIf(String::isNotBlank) ?: JSONObject.NULL)
                    put("language", track.lang.takeIf(String::isNotBlank) ?: JSONObject.NULL)
                    put("headers", JSONObject(headers))
                }
            }))
        }
    }

    /** Streams are usually known by extension; bare URLs (a local proxy, say) are sniffed. */
    private suspend fun streamType(url: String, headers: Map<String, String>): String {
        val lower = url.lowercase()
        val path = lower.substringBefore('?').substringBefore('#')
        return when {
            ".m3u8" in lower -> "DIRECT_HLS"
            path.endsWith(".mpd") -> "DIRECT_DASH"
            MEDIA_EXTENSIONS.any(path::endsWith) -> "DIRECT_MP4"
            else -> typeCache[url] ?: sniffType(url, headers).also { typeCache[url] = it }
        }
    }

    private suspend fun sniffType(url: String, headers: Map<String, String>): String {
        val request = Request.Builder().url(url).header("Range", "bytes=0-255").apply { headers.forEach { (name, value) -> header(name, value) } }.build()
        return withTimeoutOrNull(SNIFF_TIMEOUT_MS) {
            runCatching {
                NetworkHelper.instance().client.newCall(request).await().use { response ->
                    val contentType = response.header("Content-Type").orEmpty().lowercase()
                    val head = response.body.source().let { it.request(256); it.buffer.snapshot().utf8() }
                    when {
                        contentType.contains("mpegurl") || head.startsWith("#EXTM3U") -> "DIRECT_HLS"
                        contentType.contains("dash+xml") || head.contains("<MPD") -> "DIRECT_DASH"
                        else -> "DIRECT_MP4"
                    }
                }
            }.getOrDefault("DIRECT_MP4")
        } ?: "DIRECT_MP4"
    }

    /**
     * The v16 flow: the episode's hosters, then each hoster's videos, then lazy videos finished by the
     * source. One failing hoster must not hide the others; its error surfaces only if nothing worked.
     */
    private suspend fun loadVideos(episode: SEpisode): List<HostedVideo> {
        val http = source as? AnimeHttpSource
        val hosters = guarded("hoster list") { source.getHosterList(episode) }
            .let { list -> http?.let { runCatching { it.sortedHosters(list) }.getOrDefault(list) } ?: list }
        var firstError: Throwable? = null
        val listed = coroutineScope {
            hosters.map { hoster ->
                val hosterName = hoster.hosterName.trim().takeIf { it.isNotEmpty() && it != Hoster.NO_HOSTER_LIST }
                async {
                    try {
                        val videos = hoster.videoList
                            ?.let { preset -> http?.let { runCatching { it.sortedVideos(preset) }.getOrDefault(preset) } ?: preset }
                            ?: guarded("video list") { source.getVideoList(hoster) }
                        videos.map { HostedVideo(it, hosterName) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.w(TAG, "${source.name}: hoster ${hosterName ?: "-"} failed: ${error.message}")
                        if (firstError == null) firstError = error
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
        }
        val resolved = coroutineScope {
            listed.chunked(RESOLVE_PARALLELISM).flatMap { batch ->
                batch.map { hosted ->
                    val video = hosted.video
                    async {
                        if (http == null || (video.videoUrl.isNotBlank() && video.internalData.isBlank())) {
                            hosted
                        } else {
                            try {
                                guarded("video resolve") {
                                    val finished = http.resolveVideo(video)
                                    // v14 sources list a page and fetch the stream URL separately.
                                    if (finished != null && finished.videoUrl.isBlank() && finished.url.isNotBlank()) {
                                        @Suppress("DEPRECATION")
                                        val streamUrl = http.getVideoUrl(finished).takeIf(String::isNotBlank)
                                        streamUrl?.let { finished.withStreamUrl(it) } ?: finished
                                    } else {
                                        finished
                                    }
                                }?.let { hosted.copy(video = it) }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                Log.w(TAG, "${source.name}: resolving a video failed: ${error.message}")
                                if (firstError == null) firstError = error
                                null
                            }
                        }
                    }
                }.awaitAll()
            }.filterNotNull()
        }
        if (resolved.isEmpty()) firstError?.let { throw it }
        return resolved
    }

    private fun Video.withStreamUrl(streamUrl: String) = Video(
        videoUrl = streamUrl,
        videoTitle = videoTitle,
        resolution = resolution,
        bitrate = bitrate,
        headers = headers,
        preferred = preferred,
        subtitleTracks = subtitleTracks,
        audioTracks = audioTracks,
        timestamps = timestamps,
        internalData = internalData,
        initialized = true,
    )

    private suspend fun <T> guarded(operation: String, block: suspend () -> T): T {
        val work = scope.async { block() }
        try {
            return withTimeoutOrNull(OPERATION_TIMEOUT_MS) { work.await() }
                ?: throw ApkSourceException("${source.name} did not finish $operation within ${OPERATION_TIMEOUT_MS / 1000}s")
        } catch (error: CancellationException) {
            throw error
        } catch (error: ApkSourceException) {
            throw error
        } catch (error: LinkageError) {
            throw ApkSourceException("${source.name} needs something the app does not provide: ${error.message ?: error.javaClass.simpleName}", error)
        } catch (error: StackOverflowError) {
            throw ApkSourceException("${source.name} overflowed the stack during $operation", error)
        } finally {
            work.cancel()
        }
    }

    private class CachedPages {
        val titles = mutableListOf<JSONObject>()
        var nextPage = 1
        var finished = false
    }

    companion object {
        private const val TAG = "ApkSource"
        const val SORT_POPULAR = "popular"
        const val SORT_LATEST = "latest"
        private const val MAX_PAGE_SIZE = 100
        private const val MAX_CACHED_QUERIES = 12
        private const val OPERATION_TIMEOUT_MS = 90_000L
        private const val RESOLVE_PARALLELISM = 4
        private const val SNIFF_TIMEOUT_MS = 8_000L
        private val MEDIA_EXTENSIONS = listOf(".mp4", ".m4v", ".mkv", ".webm", ".mov", ".avi", ".ts", ".flv")
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private fun formatNumber(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

        /** Openings (and mixed ones) skip as openings, endings as endings; the rest stays unknown. Malformed ranges are dropped. */
        private fun toSegment(timestamp: TimeStamp): JSONObject? {
            val start = timestamp.start
            val end = timestamp.end
            if (!start.isFinite() || !end.isFinite() || start < 0.0 || end <= start) return null
            val type = when (timestamp.type) {
                ChapterType.Opening, ChapterType.MixedOp -> "OPENING"
                ChapterType.Ending -> "ENDING"
                ChapterType.Recap, ChapterType.Other -> "UNKNOWN"
            }
            return JSONObject().put("type", type).put("startMs", Math.round(start * 1000)).put("endMs", Math.round(end * 1000))
        }
    }
}

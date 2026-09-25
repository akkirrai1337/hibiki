package org.akkirrai.hibiki.core.source.extension

import org.akkirrai.hibiki.core.log.AppLogger
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.NetworkFailureLog
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import okhttp3.Request
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.akkirrai.beakokit.api.AnimeSource
import org.akkirrai.beakokit.api.LatestSource
import org.akkirrai.beakokit.api.PlaybackGroup
import org.akkirrai.beakokit.api.PlaybackSource
import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceInfo
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.CatalogFeature
import org.akkirrai.beakokit.model.CatalogCapabilities
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.SubtitleTrack
import org.akkirrai.beakokit.model.VideoSegment
import org.akkirrai.beakokit.model.VideoSegmentType
import eu.kanade.tachiyomi.animesource.model.ChapterType
import eu.kanade.tachiyomi.animesource.model.TimeStamp
import java.util.concurrent.ConcurrentHashMap
import java.util.LinkedHashMap

/** Adapts the stable Aniyomi catalogue contract to Hibiki's catalog and details API. */
class AniyomiAnimeSourceAdapter(
    val packageName: String,
    private val source: AnimeCatalogueSource,
) : AnimeSource, LatestSource, PlaybackSource {
    override val info: SourceInfo = SourceInfo(
        id = sourceId(packageName, source.id),
        name = source.name,
        languages = setOf(source.lang.toSourceLanguage()),
        primaryLanguage = source.lang.toSourceLanguage(),
        capabilities = buildSet {
            add(SourceCapability.PLAYBACK)
            if (source.supportsLatest) add(SourceCapability.LATEST_RELEASES)
        },
    )

    override val catalogCapabilities = CatalogCapabilities(
        supportedSorts = setOf(AnimeSearchSort.RELEVANCE),
        supportedFilters = emptySet(),
        features = if (source.supportsLatest) setOf(CatalogFeature.LATEST_RELEASES) else emptySet(),
    )

    /** The extension's settings screen, when it declares one (Aniyomi's ConfigurableAnimeSource). */
    val configurableSource: ConfigurableAnimeSource? get() = source as? ConfigurableAnimeSource

    /** The Aniyomi source id, which names the SharedPreferences file the extension reads. */
    val aniyomiSourceId: Long get() = source.id

    private val pageCache = LinkedHashMap<String, CachedPages>(16, 0.75f, true)
    private val paginationMutex = Mutex()
    private val fetchTypes = ConcurrentHashMap<String, FetchType>()
    private val typeCache = ConcurrentHashMap<String, PlayerType>()

    override suspend fun search(query: String): List<AnimeTitle> =
        search(AnimeSearchRequest(query = query))

    override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> {
        val limit = request.limit.coerceIn(0, MAX_PAGE_SIZE)
        if (limit == 0) return emptyList()
        val offset = request.offset.coerceAtLeast(0)
        // The same query with different filters is a different result list, so filters are part of the key.
        val key = request.query.trim() + "|filters|" + AniyomiFilterMapper.cacheKey(request.sourceFilterValues)
        return guarded("search") { paginationMutex.withLock {
            val cached = pageCache.getOrPut(key) { CachedPages() }
            while (cached.titles.size.toLong() < offset.toLong() + limit && !cached.finished) {
                val page = if (cached.titles.isEmpty()) 1 else cached.nextPage
                val result = if (request.query.isBlank() && request.sourceFilterValues.isEmpty()) {
                    source.getPopularAnime(page)
                } else {
                    // A fresh list per request: the extension mutates filter state in place.
                    val filters = source.getFilterList()
                    AniyomiFilterMapper.apply(filters, request.sourceFilterValues)
                    source.getSearchAnime(page, request.query.trim(), filters)
                }
                cached.titles += result.animes.map(::toAnimeTitle)
                cached.nextPage = page + 1
                cached.finished = !result.hasNextPage || result.animes.isEmpty()
            }
            while (pageCache.size > MAX_CACHED_QUERIES) {
                pageCache.remove(pageCache.keys.first())
            }
            cached.titles.drop(offset).take(limit)
        } }
    }

    override suspend fun latest(limit: Int): List<AnimeTitle> {
        if (!source.supportsLatest || limit <= 0) return emptyList()
        return guarded("latest") {
            source.getLatestUpdates(1).animes.take(limit.coerceAtMost(MAX_PAGE_SIZE)).map(::toAnimeTitle)
        }
    }

    override suspend fun getSearchFilterCatalog(): AnimeSearchFilterCatalog = AnimeSearchFilterCatalog(
        // Only the sorts this adapter really has: a catalog that claimed rating, title and the rest would
        // offer sort buttons that change nothing, since a source sorts through its own filters instead.
        capabilities = CatalogCapabilities.FULL.copy(
            supportedSorts = catalogCapabilities.supportedSorts,
            // The app's year range would be handed to a search that ignores it; a source that can filter by
            // year has a filter for it, which the filter window shows as a slider.
            supportedFilters = emptySet(),
            features = catalogCapabilities.features,
        ),
        sourceFilters = guarded("filters") { AniyomiFilterMapper.describe(source.getFilterList()) },
    )

    override suspend fun getById(id: String): AnimeTitle {
        val anime = SAnime.create().apply {
            url = id
            title = id
        }
        return guarded("details") {
            val details = source.getAnimeDetails(anime)
            // Some extensions rewrite the URL while loading details (Anichi appends "#<number>"). The title
            // keeps the id it was asked for: the catalog, the library and the download list all know it by
            // that id, and a second spelling made one title look like two.
            toAnimeTitle(details, fallbackId = id).copy(id = id).also { rememberFetchType(it.id, details) }
        }
    }

    override suspend fun getPlaybackGroups(title: AnimeTitle): List<PlaybackGroup> {
        val anime = title.toSAnime()
        // The details call tells whether the title is listed by seasons or directly by episodes.
        val fetchType = fetchTypes[title.id]
            ?: guarded("details") { source.getAnimeDetails(anime) }.also { rememberFetchType(title.id, it) }.fetch_type
        if (fetchType == FetchType.Seasons) {
            val seasons = guarded("season list") { source.getSeasonList(anime) }
            return coroutineScope {
                seasons.mapIndexed { index, season ->
                    async {
                        val episodes = toEpisodes(guarded("episode list") { source.getEpisodeList(season) })
                        if (episodes.isEmpty()) return@async null
                        val name = runCatching { season.title }.getOrNull()?.takeIf(String::isNotBlank)
                            ?: "Season ${index + 1}"
                        PlaybackGroup(id = "${info.id.value}-season-${index + 1}", title = name, episodes = episodes)
                    }
                }.awaitAll().filterNotNull()
            }
        }
        val episodes = toEpisodes(guarded("episode list") { source.getEpisodeList(anime) })
        if (episodes.isEmpty()) return emptyList()
        return listOf(PlaybackGroup(id = info.id.value, title = source.name, episodes = episodes))
    }

    private fun toEpisodes(list: List<SEpisode>): List<Episode> = list.mapIndexedNotNull { index, episode ->
        runCatching { episode.url }.getOrNull()?.takeIf(String::isNotBlank)?.let { episodeUrl ->
            val number = episode.episode_number.toDouble()
                .takeIf { it.isFinite() && it >= 0.0 }
                ?: (index + 1).toDouble()
            Episode(
                id = episodeUrl,
                number = number,
                title = runCatching { episode.name }.getOrNull()?.takeIf(String::isNotBlank),
            )
        }
    }

    /** A video together with the name of the hoster it was listed under, if the source has hosters. */
    private data class HostedVideo(val video: Video, val hosterName: String?)

    private fun Video.withStreamUrl(streamUrl: String): Video = Video(
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

    private fun rememberFetchType(id: String, anime: SAnime) {
        fetchTypes[id] = anime.fetch_type
    }

    override suspend fun getPlayerLinks(
        title: AnimeTitle,
        group: PlaybackGroup,
        episode: Episode,
    ): List<PlayerLink> {
        val sourceEpisode = eu.kanade.tachiyomi.animesource.model.SEpisode.create().apply {
            url = episode.id
            name = episode.title ?: "Episode ${episode.number}"
            episode_number = episode.number.toFloat()
        }
        val startedAt = System.currentTimeMillis()
        val videos = loadVideos(sourceEpisode)
        val links = coroutineScope { videos.map { hosted -> async { toPlayerLink(hosted) } }.awaitAll() }.filterNotNull()
        if (links.isEmpty()) {
            // Extensions swallow their own network errors and return nothing; the client kept them.
            val cause = NetworkFailureLog.summary(startedAt)
            throw ApkExtensionRuntimeException(
                "${source.name} returned no playable videos" + (cause?.let { " ($it)" } ?: ""),
            )
        }
        return links
    }

    private suspend fun toPlayerLink(hosted: HostedVideo): PlayerLink? {
        val video = hosted.video
        run {
            val directUrl = video.videoUrl.takeIf(String::isNotBlank)
            val url = directUrl ?: video.url
            if (url.isBlank()) return null
            val headers = video.headers?.toMultimap()
                ?.mapValues { (_, values) -> values.joinToString(", ") }
                .orEmpty()
            return PlayerLink(
                url = url,
                // Aniyomi video URLs are media streams, not embed pages; only a page URL the
                // source never resolved is treated as an embed.
                type = if (directUrl != null) directPlayerType(directUrl, headers) else PlayerType.EMBED,
                quality = video.quality.takeIf(String::isNotBlank),
                headers = headers,
                // The hoster is the server or dub the video comes from, so it becomes the player the
                // user can pick; a source without hosters is one player named after the source.
                playerName = hosted.hosterName ?: source.name,
                segments = video.timestamps.mapNotNull(::toVideoSegment).sortedBy(VideoSegment::startMs),
                audioUrl = video.audioTracks.firstOrNull()?.url?.takeIf(String::isNotBlank),
                audioHeaders = headers,
                subtitles = video.subtitleTracks.filter { it.url.isNotBlank() }.map { track ->
                    SubtitleTrack(
                        url = track.url,
                        label = track.lang.takeIf(String::isNotBlank),
                        language = track.lang.takeIf(String::isNotBlank),
                    )
                },
            )
        }
    }

    /**
     * Streams are usually recognisable by extension. Extensions that serve through a local proxy
     * (for example a loopback playlist server) use bare URLs, so those are sniffed instead.
     */
    private suspend fun directPlayerType(url: String, headers: Map<String, String>): PlayerType {
        val lower = url.lowercase()
        val path = lower.substringBefore('?').substringBefore('#')
        return when {
            ".m3u8" in lower -> PlayerType.DIRECT_HLS
            path.endsWith(".mpd") -> PlayerType.DIRECT_DASH
            MEDIA_EXTENSIONS.any(path::endsWith) -> PlayerType.DIRECT_MP4
            else -> typeCache.getOrPut(url) { sniffPlayerType(url, headers) }
        }
    }

    private suspend fun sniffPlayerType(url: String, headers: Map<String, String>): PlayerType {
        val request = Request.Builder().url(url).header("Range", "bytes=0-255").apply {
            headers.forEach { (name, value) -> header(name, value) }
        }.build()
        return withTimeoutOrNull(SNIFF_TIMEOUT_MS) {
            runCatching {
                NetworkHelper.instance().client.newCall(request).await().use { response ->
                    val contentType = response.header("Content-Type").orEmpty().lowercase()
                    val head = response.body?.source()?.let { it.request(256); it.buffer.snapshot().utf8() }.orEmpty()
                    when {
                        contentType.contains("mpegurl") || head.startsWith("#EXTM3U") -> PlayerType.DIRECT_HLS
                        contentType.contains("dash+xml") || head.contains("<MPD") -> PlayerType.DIRECT_DASH
                        else -> PlayerType.DIRECT_MP4
                    }
                }
            }.getOrDefault(PlayerType.DIRECT_MP4)
        } ?: PlayerType.DIRECT_MP4
    }

    /**
     * The v16 flow: hosters for the episode, then each hoster's videos, then lazy videos resolved.
     * One failing hoster must not hide the others; its error is only surfaced when nothing worked.
     */
    private suspend fun loadVideos(episode: SEpisode): List<HostedVideo> {
        val http = source as? AnimeHttpSource
        val startedAt = System.currentTimeMillis()
        val hosters = guarded("hoster list") { source.getHosterList(episode) }
            .let { list -> http?.let { runCatching { it.sortedHosters(list) }.getOrDefault(list) } ?: list }
        AppLogger.d(TAG, "${source.name}: ${hosters.size} hosters in ${System.currentTimeMillis() - startedAt}ms")
        var firstError: Throwable? = null
        val listed = coroutineScope {
            hosters.map { hoster ->
                val hosterName = hoster.hosterName.trim().takeIf { it.isNotEmpty() && it != Hoster.NO_HOSTER_LIST }
                async {
                    try {
                        // Videos returned by the source for a hoster are already ordered by its own hook.
                        val hosterStartedAt = System.currentTimeMillis()
                        val videos = hoster.videoList
                            ?.let { preset -> http?.let { runCatching { it.sortedVideos(preset) }.getOrDefault(preset) } ?: preset }
                            ?: guarded("video list") { source.getVideoList(hoster) }
                        AppLogger.d(TAG, "${source.name}: hoster ${hosterName ?: "-"} gave ${videos.size} videos in ${System.currentTimeMillis() - hosterStartedAt}ms")
                        videos.map { HostedVideo(it, hosterName) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        AppLogger.w(TAG, "${source.name}: hoster ${hosterName ?: "-"} failed: ${error.message}")
                        if (firstError == null) firstError = error
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
        }
        val ordered = listed
        // Lazy videos carry internalData (or no URL yet) and are finished by the source on demand.
        val resolved = coroutineScope {
            ordered.chunked(RESOLVE_PARALLELISM).flatMap { batch ->
                batch.map { hosted ->
                    val video = hosted.video
                    async {
                        if (http == null || (video.videoUrl.isNotBlank() && video.internalData.isBlank())) {
                            hosted
                        } else {
                            try {
                                val finished = guarded("video resolve") {
                                    val resolvedVideo = http.resolveVideo(video)
                                    // v14 sources list a page and fetch the stream URL separately.
                                    if (resolvedVideo != null && resolvedVideo.videoUrl.isBlank() && resolvedVideo.url.isNotBlank()) {
                                        @Suppress("DEPRECATION")
                                        val streamUrl = http.getVideoUrl(resolvedVideo).takeIf(String::isNotBlank)
                                        streamUrl?.let { resolvedVideo.withStreamUrl(it) } ?: resolvedVideo
                                    } else {
                                        resolvedVideo
                                    }
                                }
                                finished?.let { hosted.copy(video = it) }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                AppLogger.w(TAG, "${source.name}: resolving a video failed: ${error.message}")
                                if (firstError == null) firstError = error
                                null
                            }
                        }
                    }
                }.awaitAll()
            }.filterNotNull()
        }
        AppLogger.d(TAG, "${source.name}: ${resolved.size} of ${ordered.size} videos resolved, ${System.currentTimeMillis() - startedAt}ms in total")
        if (resolved.isEmpty()) firstError?.let { throw it }
        return resolved
    }

    /**
     * Runs extension code off the caller's thread with a hard deadline. Extensions are third-party
     * code: they may block (QuickJS, synchronous OkHttp), hang, or throw linkage errors that would
     * otherwise escape coroutine exception handling and kill the process.
     */
    private suspend fun <T> guarded(operation: String, block: suspend () -> T): T {
        val work = extensionScope.async { block() }
        try {
            return withTimeoutOrNull(OPERATION_TIMEOUT_MS) { work.await() }
                ?: throw ApkExtensionRuntimeException(
                    "${source.name} did not finish $operation within ${OPERATION_TIMEOUT_MS / 1000}s",
                )
        } catch (error: CancellationException) {
            throw error
        } catch (error: ApkExtensionRuntimeException) {
            throw error
        } catch (error: LinkageError) {
            throw ApkExtensionRuntimeException(
                "${source.name} needs a runtime class Hibiki does not provide: " +
                    (error.message ?: error.javaClass.simpleName),
                error,
            )
        } catch (error: StackOverflowError) {
            throw ApkExtensionRuntimeException("${source.name} overflowed the stack during $operation", error)
        } finally {
            work.cancel()
        }
    }

    private fun toAnimeTitle(anime: SAnime, fallbackId: String? = null): AnimeTitle {
        // Some third-party Aniyomi extensions return a fresh SAnime from getAnimeDetails()
        // without initializing its lateinit URL. Keep the requested URL as the stable ID.
        val url = runCatching { anime.url }.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: fallbackId.orEmpty()
        val title = runCatching { anime.title }.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: url
        val description = SourceDescriptionFooter.parse(anime.description)
        val facts = FooterFacts.from(description.facts)
        val credits = listOfNotNull(anime.author, anime.artist, facts.studioText).map(StudioCredits::parse)
        return AnimeTitle(
            id = url,
            originalName = title,
            posterUrl = anime.thumbnail_url,
            bannerUrl = anime.background_url,
            status = when (anime.status) {
                SAnime.ONGOING -> "ongoing"
                SAnime.COMPLETED, SAnime.PUBLISHING_FINISHED -> "completed"
                else -> null
            },
            description = description.text,
            ageRating = facts.ageRating,
            episodeCount = facts.episodeCount,
            type = facts.type,
            year = facts.year,
            season = facts.season,
            synonyms = facts.synonyms,
            genres = anime.getGenres().orEmpty(),
            // Aniyomi has no studio field; extensions that know it put it in author (or artist).
            studios = credits.flatMap { it.studios }.distinct(),
            producers = (credits.flatMap { it.producers } + StudioCredits.parse(facts.producerText).studios).distinct(),
            ratings = listOfNotNull(facts.score?.let { org.akkirrai.beakokit.model.TitleRating(source.name, it) }),
        )
    }

    private fun AnimeTitle.toSAnime(): SAnime = SAnime.create().apply {
        url = id
        title = originalName
        description = this@toSAnime.description
        genre = genres.joinToString(", ")
        thumbnail_url = posterUrl
        background_url = bannerUrl
        status = when (this@toSAnime.status?.lowercase()) {
            "ongoing", "airing", "is_ongoing" -> SAnime.ONGOING
            "completed", "finished", "released", "is_not_ongoing" -> SAnime.COMPLETED
            else -> SAnime.UNKNOWN
        }
        initialized = true
    }

    private class CachedPages {
        val titles = mutableListOf<AnimeTitle>()
        var nextPage: Int = 1
        var finished: Boolean = false
    }

    private companion object {
        const val MAX_PAGE_SIZE = 100
        const val MAX_CACHED_QUERIES = 12
        const val TAG = "AniyomiAdapter"
        const val OPERATION_TIMEOUT_MS = 90_000L
        const val RESOLVE_PARALLELISM = 4

        val extensionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun sourceId(packageName: String, sourceId: Long): SourceId {
            val packageSlug = packageName.lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .take(48)
            val packageHash = java.lang.Integer.toUnsignedString(packageName.hashCode(), 36)
            return SourceId("aniyomi-$packageSlug-$packageHash-${java.lang.Long.toUnsignedString(sourceId, 36)}")
        }

        fun String.toSourceLanguage(): SourceLanguage {
            val normalized = lowercase().replace('_', '-')
            return runCatching { SourceLanguage(normalized) }.getOrDefault(SourceLanguage.ENGLISH)
        }

        val MEDIA_EXTENSIONS = listOf(".mp4", ".m4v", ".mkv", ".webm", ".mov", ".avi", ".ts", ".flv")
        const val SNIFF_TIMEOUT_MS = 8_000L
    }
}

/**
 * An extension timestamp (seconds) as a skippable segment (milliseconds). Openings and mixed
 * openings can be skipped as an opening, endings as an ending; recaps and unnamed chapters are
 * kept as unknown segments. Malformed ranges are dropped rather than trusted.
 */
internal fun toVideoSegment(timestamp: TimeStamp): VideoSegment? {
    val start = timestamp.start
    val end = timestamp.end
    if (!start.isFinite() || !end.isFinite() || start < 0.0 || end <= start) return null
    val type = when (timestamp.type) {
        ChapterType.Opening, ChapterType.MixedOp -> VideoSegmentType.OPENING
        ChapterType.Ending -> VideoSegmentType.ENDING
        ChapterType.Recap, ChapterType.Other -> VideoSegmentType.UNKNOWN
    }
    return VideoSegment(type = type, startMs = Math.round(start * 1000), endMs = Math.round(end * 1000))
}

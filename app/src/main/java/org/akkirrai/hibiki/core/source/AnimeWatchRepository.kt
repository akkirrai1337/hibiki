package org.akkirrai.hibiki.core.source

import android.content.Context
import android.os.SystemClock
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.akkirrai.beakokit.api.StreamExtractor
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.beakokit.playback.extractor.DirectHlsExtractor
import org.akkirrai.beakokit.playback.extractor.DirectMp4Extractor
import org.akkirrai.beakokit.playback.PlaybackResolver
import org.akkirrai.beakokit.extension.BrowserScriptResolver
import org.akkirrai.beakokit.extension.BrowserPlaybackMode
import org.akkirrai.beakokit.playback.validation.HttpStreamValidator
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.StreamType
import org.akkirrai.beakokit.model.VideoSegment
import org.akkirrai.beakokit.model.VideoSegmentType
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.app.settings.LanguageMode
import org.akkirrai.hibiki.core.model.PlaybackLinkOption
import org.akkirrai.hibiki.core.model.PlaybackSegment
import org.akkirrai.hibiki.core.model.PlaybackSegmentType
import org.akkirrai.hibiki.core.model.PlaybackSubtitle
import org.akkirrai.hibiki.core.model.PlaybackSettingsOptions
import org.akkirrai.hibiki.core.model.PlaybackStream
import org.akkirrai.hibiki.core.model.PlaybackStreamType
import org.akkirrai.hibiki.core.model.WatchEpisode
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.model.WatchSource
import org.akkirrai.hibiki.core.network.AndroidHttpClientFactory
import org.akkirrai.hibiki.core.network.NoInternetConnectionException
import org.akkirrai.hibiki.core.network.hasActiveInternetConnection
import org.akkirrai.beakokit.api.PlaybackGroup
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

data class WatchSourcesCacheSnapshot(
    val sources: List<WatchSource>,
)

data class ResolvedPlayerStream(
    val playerName: String?,
    val playback: PlaybackStream,
)

internal const val WATCH_SOURCE_SEPARATOR = "|watch|"

internal fun watchTitleIdFromSourceId(sourceId: String): String =
    if (WATCH_SOURCE_SEPARATOR in sourceId) {
        sourceId.substringBefore(WATCH_SOURCE_SEPARATOR)
    } else {
        sourceId.substringBefore(':')
    }

class AnimeWatchRepository(
    context: Context? = null,
    private val client: HttpClient = AndroidHttpClientFactory.create(),
    sourceManager: AnimeSourceRuntimeManager? = null,
    private val closeClientOnClose: Boolean = true,
) {
    private val cachedSources = ConcurrentHashMap<String, CachedWatchSources>()
    private val sourcePayloads = ConcurrentHashMap<String, SourcePayload>()
    private val sourcePayloadLanguages = ConcurrentHashMap<String, String>()
    private val cachedStreams = ConcurrentHashMap<String, CachedPlaybackStream>()
    private val cachedPlayerLinks = ConcurrentHashMap<String, CachedPlayerLinks>()
    private val unavailablePlayerLinks = ConcurrentHashMap<String, Long>()
    private val inFlightLoads = ConcurrentHashMap<String, CompletableDeferred<List<WatchSource>>>()
    private val inFlightPlayerLinks = ConcurrentHashMap<String, CompletableDeferred<List<PlayerLink>>>()
    private val appContext = context?.applicationContext
    private val appPreferences = appContext?.let(::AppPreferences)
    private val sourceManager = sourceManager ?: appContext?.let { AnimeSourceRuntimeManager(it, client) }
    private val validator = HttpStreamValidator(client) { stage, elapsedMs, success ->
        AppLogger.d(TAG, "[playback.validation.$stage] elapsedMs=$elapsedMs success=$success")
    }
    @Volatile
    private var extractorsGeneration = -1
    @Volatile
    private var activeExtractors: List<StreamExtractor> = emptyList()
    @Volatile
    private var activeBrowserResolvers: List<BrowserScriptResolver> = emptyList()
    private val loadMutex = Mutex()
    private val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun getCachedSources(animeId: String): WatchSourcesCacheSnapshot? {
        val canonicalId = extractTitleId(animeId)
        // languageCacheKey resolves the title's source runtime just to build the key - with no
        // source installed (or none matching this title) that throws SourceException; since this
        // is a synchronous best-effort cache peek called eagerly from ViewModel init (not inside a
        // suspend block a ViewModel could catch around), treat "can't resolve a source" as "cache
        // miss" rather than letting it crash composition.
        val cacheKey = runCatching { languageCacheKey(canonicalId) }.getOrNull() ?: return null
        val cached = cachedSources[cacheKey] ?: return null
        return WatchSourcesCacheSnapshot(sources = cached.sources)
    }

    suspend fun loadSources(
        animeId: String,
        onUpdate: (List<WatchSource>) -> Unit,
    ): List<WatchSource> {
        val canonicalId = resolveAnimeId(animeId)
        val cacheKey = languageCacheKey(canonicalId)
        cachedSources[cacheKey]?.let {
            onUpdate(it.sources)
            return it.sources
        }

        val (inFlight, isCreator) = loadMutex.withLock {
            inFlightLoads[cacheKey]?.let { existing ->
                existing to false
            } ?: CompletableDeferred<List<WatchSource>>().also { created ->
                inFlightLoads[cacheKey] = created
            }.let { created ->
                created to true
            }
        }

        if (isCreator) {
            runCatching {
                ensureInternetConnection()
                val sources = performLoadSources(canonicalId)
                cachedSources[cacheKey] = CachedWatchSources(sources)
                onUpdate(sources)
                inFlight.complete(sources)
                sources
            }.onFailure { error ->
                inFlight.completeExceptionally(error)
                if (error is CancellationException) throw error
            }
            loadMutex.withLock {
                if (inFlightLoads[cacheKey] === inFlight) {
                    inFlightLoads.remove(cacheKey)
                }
            }
        }

        val result = inFlight.await()
        onUpdate(result)
        return result
    }

    suspend fun getEpisodes(sourceId: String): List<WatchEpisode> {
        val payload = ensureSourcePayload(sourceId) ?: return emptyList()
        return payload.episodes
            .sortedBy(Episode::number)
            .map { episode ->
                WatchEpisode(
                    id = episode.id,
                    number = episode.number,
                    title = episode.title,
                )
            }
    }

    fun getCachedEpisodes(sourceId: String): List<WatchEpisode>? {
        val payload = sourcePayloads[sourceId] ?: return null
        return payload.episodes
            .sortedBy(Episode::number)
            .map { episode ->
                WatchEpisode(
                    id = episode.id,
                    number = episode.number,
                    title = episode.title,
                )
            }
    }

    suspend fun resolveStream(
        sourceId: String,
        episodeId: String,
        forceRefresh: Boolean = false,
        excludedStreamUrls: Set<String> = emptySet(),
        preferredPlayerName: String? = null,
        preferredQuality: String? = null,
        requiredPlayerName: String? = null,
    ): PlaybackStream = resolveStreamInternal(
        sourceId = sourceId,
        episodeId = episodeId,
        forceRefresh = forceRefresh,
        excludedStreamUrls = excludedStreamUrls,
        preferredPlayerName = preferredPlayerName,
        preferredQuality = preferredQuality,
        requiredPlayerName = requiredPlayerName,
    ).playback

    private suspend fun resolveStreamInternal(
        sourceId: String,
        episodeId: String,
        forceRefresh: Boolean = false,
        excludedStreamUrls: Set<String> = emptySet(),
        preferredPlayerName: String? = null,
        preferredQuality: String? = null,
        requiredPlayerName: String? = null,
        selectFirstAvailablePlayer: Boolean = false,
    ): ResolvedPlayerStream {
        val resolveStartedAt = SystemClock.elapsedRealtime()
        val cacheKey = "$sourceId:$episodeId:${preferredPlayerName.orEmpty()}:${preferredQuality.orEmpty()}:${requiredPlayerName.orEmpty()}"
        if (!forceRefresh) {
            cachedStreams[cacheKey]
                ?.takeIf { System.currentTimeMillis() - it.cachedAt < STREAM_CACHE_TTL_MS }
                ?.takeIf { it.stream.streamUrl !in excludedStreamUrls }
                ?.let {
                    AppLogger.d(TAG, "[playback.resolve.cache_hit] elapsedMs=${SystemClock.elapsedRealtime() - resolveStartedAt}")
                    return ResolvedPlayerStream(it.playerName, it.stream)
                }
        }

        val internetCheckStartedAt = SystemClock.elapsedRealtime()
        ensureInternetConnection()
        val internetCheckElapsedMs = SystemClock.elapsedRealtime() - internetCheckStartedAt

        // KAA's selected source id and episode id already carry everything its player-link API
        // needs. Avoid downloading every locale and episode page merely to start this one episode;
        // the complete list remains lazy for the settings panel. Fall back for any source whose
        // group identity cannot be reconstructed safely.
        val payloadStartedAt = SystemClock.elapsedRealtime()
        val directPayload = knownKickAssPayload(sourceId, episodeId)
        val payload = directPayload ?: ensureSourcePayload(sourceId)
            ?: throw SourceException(appString(R.string.watch_error_voiceover_not_found))
        val episode = directPayload?.episodes?.single()
            ?: payload.episodes.firstOrNull { it.id == episodeId }
            ?: throw SourceException(appString(R.string.watch_error_episode_not_found))
        AppLogger.d(
            TAG,
            "[playback.payload.ready] internetCheckMs=$internetCheckElapsedMs " +
                "payloadMs=${SystemClock.elapsedRealtime() - payloadStartedAt}, " +
                "directEpisode=${directPayload != null} episodeCount=${payload.episodes.size}",
        )
        // A selected voiceover is an explicit user choice. Trying unrelated sibling groups made
        // a broken provider look like an endless load while spending the entire resolution budget
        // on streams the user did not choose.
        val candidates = listOf(payload to episode)
        val resolver = PlaybackResolver(currentExtractors(), validator)
        var resolvedCandidate: Pair<org.akkirrai.beakokit.playback.ResolvedPlaybackStream, SourcePayload>? = null
        var resolvedPlayerName: String? = null
        var browserPageCandidate: ResolvedPlayerStream? = null
        var lastResolutionError: Throwable? = null
        try {
            withTimeout(EPISODE_RESOLUTION_TIMEOUT_MS) {
                for ((candidateIndex, candidate) in candidates.withIndex()) {
                    val (candidatePayload, candidateEpisode) = candidate
                    val startedAt = System.currentTimeMillis()
                    AppLogger.d(
                        TAG,
                        "Playback attempt ${candidateIndex + 1}/${candidates.size}: voiceover=${candidatePayload.source.title}, " +
                            "sourceId=${candidatePayload.source.sourceId}, episode=${candidateEpisode.number}, " +
                            "requiredPlayer=${requiredPlayerName.orEmpty()}",
                    )
                    val rawLinks = try {
                        withTimeout(PLAYER_LINK_DISCOVERY_TIMEOUT_MS) {
                            getFilteredLinks(candidatePayload, candidateEpisode, forceRefresh)
                        }
                    } catch (error: CancellationException) {
                        if (error !is TimeoutCancellationException) throw error
                        AppLogger.w(TAG, "Playback attempt ${candidateIndex + 1}/${candidates.size} timed out discovering links: voiceover=${candidatePayload.source.title}")
                        emptyList()
                    }
                    AppLogger.d(
                        TAG,
                        "Playback attempt ${candidateIndex + 1}/${candidates.size} links ready: " +
                            "count=${rawLinks.size}, elapsedMs=${System.currentTimeMillis() - startedAt}",
                    )
                    val availableLinks = rawLinks.filterNot { it.url in excludedStreamUrls }
                    val selectedAutomaticPlayer = if (selectFirstAvailablePlayer) {
                        val playerNames = prioritizeLinks(
                            links = availableLinks,
                            preferredPlayerName = null,
                            preferredQuality = null,
                        ).mapNotNull { it.playerName?.trim()?.takeIf(String::isNotBlank) }
                            .distinctBy(String::lowercase)
                        preferredPlayerName
                            ?.takeIf { preferred -> playerNames.any { matchesPreferredPlayer(it, preferred) } }
                            ?: playerNames.firstOrNull()
                    } else {
                        null
                    }
                    val effectivePreferredPlayer = if (selectFirstAvailablePlayer) {
                        selectedAutomaticPlayer
                    } else {
                        preferredPlayerName
                    }
                    val links = prioritizeLinks(
                        links = availableLinks,
                        preferredPlayerName = effectivePreferredPlayer,
                        preferredQuality = preferredQuality,
                    ).filter { link ->
                        when {
                            selectFirstAvailablePlayer && !selectedAutomaticPlayer.isNullOrBlank() ->
                                matchesPreferredPlayer(link.playerName, selectedAutomaticPlayer)
                            !requiredPlayerName.isNullOrBlank() ->
                                matchesPreferredPlayer(link.playerName, requiredPlayerName)
                            else -> true
                        }
                    }
                    if (selectFirstAvailablePlayer) {
                        AppLogger.d(TAG, "[playback.player.selected] name=${selectedAutomaticPlayer.orEmpty()} links=${links.size}")
                    }
                    if (links.isEmpty()) {
                        AppLogger.d(TAG, "Playback attempt ${candidateIndex + 1}/${candidates.size} skipped: voiceover=${candidatePayload.source.title}, no playable links, elapsedMs=${System.currentTimeMillis() - startedAt}")
                        continue
                    }
                    // Some providers intentionally keep media inside their browser player. For
                    // those resolvers, rendering that page is the playable result; attempting a
                    // second HLS extraction only turns a working embed into a timeout.
                    browserPagePlayback(links, candidatePayload, candidateEpisode)?.let { pagePlayback ->
                        val playerName = links.firstOrNull()?.playerName
                        cachedStreams[cacheKey] = CachedPlaybackStream(pagePlayback, playerName, System.currentTimeMillis())
                        AppLogger.d(TAG, "Playback attempt ${candidateIndex + 1}/${candidates.size} uses browser-page playback: voiceover=${candidatePayload.source.title}")
                        browserPageCandidate = ResolvedPlayerStream(playerName, pagePlayback)
                        break
                    }
                    try {
                        val extractionStartedAt = SystemClock.elapsedRealtime()
                        val candidateResolved = try {
                            resolver.resolve(
                                links = links,
                                excludedStreamUrls = excludedStreamUrls,
                                preferredQuality = preferredQuality,
                                attemptTimeoutMillis = { link ->
                                    if (candidatePayload === payload) {
                                        resolveAttemptTimeoutMillis(preferredPlayerName, link.playerName, link.type)
                                    } else {
                                        FALLBACK_RESOLVE_TIMEOUT_MS
                                    }
                                },
                            )
                        } finally {
                            AppLogger.d(
                                TAG,
                                "[playback.extract_validate] linkCount=${links.size} " +
                                    "elapsedMs=${SystemClock.elapsedRealtime() - extractionStartedAt}",
                            )
                        }
                        resolvedCandidate = candidateResolved to candidatePayload
                        resolvedPlayerName = candidateResolved.link.playerName
                        AppLogger.d(TAG, "Playback attempt ${candidateIndex + 1}/${candidates.size} succeeded: voiceover=${candidatePayload.source.title}, elapsedMs=${System.currentTimeMillis() - startedAt}")
                        break
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        lastResolutionError = error
                        AppLogger.w(
                            TAG,
                            "Playback attempt ${candidateIndex + 1}/${candidates.size} failed: voiceover=${candidatePayload.source.title}, " +
                                "elapsedMs=${System.currentTimeMillis() - startedAt}, error=${error.message}",
                        )
                    }
                }
            }
        } catch (error: TimeoutCancellationException) {
            AppLogger.w(TAG, "Episode playback resolution timed out after ${EPISODE_RESOLUTION_TIMEOUT_MS}ms; attempted ${candidates.size} voiceovers")
            lastResolutionError = SourceException("Playback resolution timed out")
        }
        browserPageCandidate?.let { return it }
        val (resolved, resolvedPayload) = resolvedCandidate ?: run {
            if (!requiredPlayerName.isNullOrBlank()) {
                throw SourceException(appString(R.string.watch_error_selected_player_unavailable))
            }
            throw lastResolutionError ?: SourceException(appString(R.string.watch_error_no_players))
        }
        AppLogger.d(
            TAG,
            "validated stream: player=${resolved.link.playerName}, type=${resolved.validation.streamType}, " +
                "quality=${resolved.validation.quality}, status=${resolved.validation.statusCode}, " +
                "streamHost=${resolved.validation.finalUrl.safeHost()}, " +
                "headerNames=${resolved.stream.headers.safeHeaderNames()}",
        )
        val playback = PlaybackStream(
            animeTitle = payload.title.displayName,
            sourceTitle = resolvedPayload.source.title,
            episodeTitle = episode.title?.takeIf(String::isNotBlank)
                ?: appString(R.string.watch_episode_fallback_title, episode.number.formatEpisodeNumber()),
            streamUrl = resolved.validation.finalUrl,
            streamType = resolved.validation.streamType.toPlaybackType(),
            qualityLabel = resolved.validation.quality ?: resolved.stream.quality ?: resolved.link.quality,
            availableQualityLabels = (
                resolved.availableQualityLabels + (resolved.validation.quality ?: resolved.stream.quality ?: resolved.link.quality)
            ).mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }.distinct(),
            headers = resolved.stream.headers.ifEmpty { resolved.link.headers },
            audioStreamUrl = resolved.stream.audioUrl,
            audioHeaders = resolved.stream.audioHeaders,
            subtitles = resolved.stream.subtitles.map { subtitle ->
                PlaybackSubtitle(
                    url = subtitle.url,
                    label = subtitle.label,
                    language = subtitle.language,
                    headers = subtitle.headers,
                )
            },
            segments = selectPlaybackSegments(
                apiSegments = resolved.link.segments,
                extractedSegments = resolved.stream.segments,
            ).map { segment -> segment.toPlaybackSegment() },
            videoId = resolved.link.videoId,
        )
        val playerName = resolvedPlayerName ?: resolved.link.playerName
        cachedStreams[cacheKey] = CachedPlaybackStream(
            stream = playback,
            playerName = playerName,
            cachedAt = System.currentTimeMillis(),
        )
        return ResolvedPlayerStream(playerName, playback)
    }

    suspend fun resolveFastestStream(
        sourceId: String,
        episodeId: String,
        forceRefresh: Boolean = false,
        /** Streams already known to be unplayable, so an automatic retry reaches a different
         * candidate instead of resolving its way back to the one that just failed. */
        excludedStreamUrls: Set<String> = emptySet(),
        preferredPlayerName: String? = null,
        preferredQuality: String? = null,
    ): ResolvedPlayerStream {
        // Automatic startup resolves exactly one player. Racing every embed put needless load on
        // the source and made a non-cancellable losing resolver hold the winning stream hostage.
        // Select from the links already loaded for resolution instead of preloading settings options
        // (which fetched those same links once before this call).
        return resolveStreamInternal(
            sourceId = sourceId,
            episodeId = episodeId,
            forceRefresh = forceRefresh,
            excludedStreamUrls = excludedStreamUrls,
            preferredPlayerName = preferredPlayerName,
            preferredQuality = preferredQuality,
            selectFirstAvailablePlayer = true,
        )
    }

    suspend fun getPlaybackSettingsOptions(
        sourceId: String,
        episodeId: String,
    ): PlaybackSettingsOptions {
        val titleId = extractTitleId(sourceId)
        if (titleId.isBlank()) return PlaybackSettingsOptions()

        ensureInternetConnection()

        val voiceovers = cachedSources[titleId]?.sources
            ?: loadSources(titleId, onUpdate = {})
        val payload = ensureSourcePayload(sourceId) ?: return PlaybackSettingsOptions(voiceovers = voiceovers)
        val episode = payload.episodes.firstOrNull { it.id == episodeId }
            ?: return PlaybackSettingsOptions(voiceovers = voiceovers)
        val links = getFilteredLinks(payload, episode)
        val resolvedLinkOptions = prioritizeLinks(
            links = links,
            preferredPlayerName = null,
            preferredQuality = null,
        ).map { link ->
            PlaybackLinkOption(
                playerName = link.playerName,
                qualityLabel = link.quality,
            )
        }

        return PlaybackSettingsOptions(
            voiceovers = voiceovers,
            links = resolvedLinkOptions.distinct(),
        )
    }

    fun clearCaches() {
        cachedSources.clear()
        sourcePayloads.clear()
        sourcePayloadLanguages.clear()
        cachedStreams.clear()
        cachedPlayerLinks.clear()
        unavailablePlayerLinks.clear()
        inFlightLoads.clear()
        inFlightPlayerLinks.clear()
    }

    fun close() {
        clearCaches()
        requestScope.cancel()
        if (closeClientOnClose) client.close()
    }

    private suspend fun performLoadSources(animeId: String): List<WatchSource> {
        val runtime = sourceForTitle(animeId)
        val title = runtime.details(animeId)
        if (!runtime.supportsPlayback) {
            throw SourceException(appString(R.string.watch_error_no_voiceovers_from_source))
        }
        val groups = runCatching { runtime.getPlaybackGroups(title) }
            .onFailure { error ->
                AppLogger.w(TAG, "${runtime.descriptor.name} source discovery failed: ${error.message}")
            }
            .getOrDefault(emptyList())
        val sources = groups.mapIndexed { index, group ->
            val source = WatchSource(
                sourceId = buildSourceId(animeId, group.title, index),
                title = group.title,
                episodeCount = group.episodes.size,
                qualityLabel = group.qualityLabel,
                isPriority = index == 0,
            )
            sourcePayloads[source.sourceId] = SourcePayload(
                source = source,
                order = index,
                animeId = animeId,
                title = title,
                group = group,
                episodes = group.episodes.sortedBy(Episode::number),
                runtime = runtime,
            )
            sourcePayloadLanguages[source.sourceId] = sourceLanguageKey(animeId)
            source
        }
        val allSources = listOfNotNull(sources.firstOrNull { it.isPriority }) +
            sources.filterNot { it.isPriority }
        if (allSources.isEmpty()) {
            throw SourceException(appString(R.string.watch_error_no_voiceovers_from_source))
        }
        return allSources
    }

    private fun sourceForTitle(titleId: String): AnimeSourceRuntime = sourceManager?.forTitle(titleId)
        ?: error("Anime source selection requires an Android context")

    private fun knownKickAssPayload(sourceId: String, episodeId: String): SourcePayload? {
        val titleId = extractTitleId(sourceId)
        val runtime = sourceForTitle(titleId)
        if (runtime.descriptor.id.value != "kickassanime") return null
        val title = runtime.cachedDetailsFor(titleId) ?: return null
        val groupSlug = sourceId.substringAfter(WATCH_SOURCE_SEPARATOR, "")
            .substringBeforeLast('-', "")
        val locale = when (groupSlug) {
            "japanese" -> "ja-JP"
            "english" -> "en-US"
            "spanish" -> "es-ES"
            "korean" -> "ko-KR"
            "chinese" -> "zh-CN"
            else -> return null
        }
        val episodeNumber = Regex("^ep-([0-9]+(?:\\.[0-9]+)?)-")
            .find(episodeId)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        val episode = Episode(id = episodeId, number = episodeNumber, title = null)
        val sourceTitle = groupSlug.replaceFirstChar(Char::uppercase)
        return SourcePayload(
            source = WatchSource(sourceId, sourceTitle, episodeCount = null, isPriority = false),
            order = 0,
            animeId = titleId,
            title = title,
            group = PlaybackGroup(id = locale, title = sourceTitle, episodes = listOf(episode)),
            episodes = listOf(episode),
            runtime = runtime,
        )
    }

    private suspend fun ensureSourcePayload(sourceId: String): SourcePayload? {
        val titleId = extractTitleId(sourceId)
        sourcePayloads[sourceId]
            ?.takeIf { sourcePayloadLanguages[sourceId] == sourceLanguageKey(titleId) }
            ?.let { return it }
        if (titleId.isBlank()) return null
        loadSources(titleId, onUpdate = {})
        return sourcePayloads[sourceId]
    }

    private suspend fun getFilteredLinks(
        payload: SourcePayload,
        episode: Episode,
        forceRefresh: Boolean = false,
    ): List<PlayerLink> {
        val cacheKey = "${payload.source.sourceId}\u0000${episode.id}"
        if (!forceRefresh) {
            unavailablePlayerLinks[cacheKey]
                ?.takeIf { System.currentTimeMillis() - it < UNAVAILABLE_PROVIDER_TTL_MS }
                ?.let {
                    AppLogger.d(TAG, "Skipping temporarily unavailable provider: sourceId=${payload.source.sourceId}, episode=${episode.number}")
                    return emptyList()
                }
        }
        unavailablePlayerLinks.remove(cacheKey)
        return loadPlayerLinks(cacheKey, forceRefresh) {
            try {
                payload.runtime.getPlayerLinks(payload.title, payload.group, episode)
                    .filter(::isSupportedLink)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (error.message?.contains("HTTP 444") == true || error.message?.contains("HTTP 5") == true) {
                    unavailablePlayerLinks[cacheKey] = System.currentTimeMillis()
                }
                AppLogger.w(
                    TAG,
                    "Selected voiceover ${payload.source.title} failed for episode ${episode.number}; " +
                        "trying sibling voiceovers: ${error.message}",
                )
                emptyList()
            }
        }
    }

    /**
     * Player controls and stream resolution ask for the same links at nearly the same time.
     * Keep that provider request independent from either caller so cancelling the settings sheet
     * cannot also cancel playback, and briefly reuse the result for subsequent control updates.
     */
    internal suspend fun loadPlayerLinks(
        cacheKey: String,
        forceRefresh: Boolean = false,
        loader: suspend () -> List<PlayerLink>,
    ): List<PlayerLink> {
        if (forceRefresh) {
            cachedPlayerLinks.remove(cacheKey)
        } else {
            cachedPlayerLinks[cacheKey]
                ?.takeIf { System.currentTimeMillis() - it.cachedAt < PLAYER_LINKS_CACHE_TTL_MS }
                ?.let { return it.links }
        }

        val created = CompletableDeferred<List<PlayerLink>>()
        val existing = inFlightPlayerLinks.putIfAbsent(cacheKey, created)
        val request = existing ?: created.also { deferred ->
            requestScope.launch {
                try {
                    val links = loader()
                    cachedPlayerLinks[cacheKey] = CachedPlayerLinks(
                        links = links,
                        cachedAt = System.currentTimeMillis(),
                    )
                    deferred.complete(links)
                } catch (error: Throwable) {
                    deferred.completeExceptionally(error)
                } finally {
                    inFlightPlayerLinks.remove(cacheKey, deferred)
                }
            }
        }
        return request.await()
    }

    private suspend fun resolveAnimeId(rawId: String): String {
        return sourceForTitle(rawId).normalizeId(rawId)
    }

    private fun extractTitleId(sourceId: String): String {
        return watchTitleIdFromSourceId(sourceId)
    }

    private fun buildSourceId(
        animeId: String,
        dubbingTitle: String,
        index: Int,
    ): String {
        val slug = dubbingTitle.lowercase()
            .replace(Regex("""[^\p{L}\p{N}]+"""), "-")
            .trim('-')
            .ifBlank { "voiceover-$index" }
        return "$animeId$WATCH_SOURCE_SEPARATOR$slug-$index"
    }

    internal fun prioritizeLinks(
        links: List<PlayerLink>,
        preferredPlayerName: String?,
        preferredQuality: String?,
    ): List<PlayerLink> {
        return links.sortedWith(
            compareBy<PlayerLink> { if (matchesPreferredPlayer(it.playerName, preferredPlayerName)) 0 else 1 }
                .thenBy { if (matchesPreferredQuality(it.quality, preferredQuality)) 0 else 1 }
                .thenBy { playerPriority(it.playerName) }
        )
    }

    internal fun resolveAttemptTimeoutMillis(
        preferredPlayerName: String?,
        candidatePlayerName: String?,
        playerType: PlayerType? = null,
    ): Long {
        if (playerType == PlayerType.DIRECT_HLS || playerType == PlayerType.DIRECT_MP4) {
            return if (matchesPreferredPlayer(candidatePlayerName, preferredPlayerName)) {
                DIRECT_PREFERRED_RESOLVE_TIMEOUT_MS
            } else {
                DIRECT_AUTO_RESOLVE_TIMEOUT_MS
            }
        }
        return if (matchesPreferredPlayer(candidatePlayerName, preferredPlayerName)) {
            PREFERRED_RESOLVE_TIMEOUT_MS
        } else {
            AUTO_RESOLVE_TIMEOUT_MS
        }
    }

    private fun playerPriority(name: String?): Int = when {
        name.containsPlayerToken("kodik") -> 0
        name.containsPlayerToken("aksor") -> 1
        name.containsPlayerToken("alloha") -> 2
        name.containsPlayerToken("sibnet") -> 3
        name.containsPlayerToken("cvh") -> 4
        name.containsPlayerToken("vk") -> 5
        name.containsPlayerToken("aniboom") -> 6
        else -> 10
    }

    private fun currentLanguageKey(): String = when (appPreferences?.state?.value?.languageMode ?: LanguageMode.SYSTEM) {
        LanguageMode.UKRAINIAN -> "uk"
        LanguageMode.ENGLISH -> "en"
        LanguageMode.RUSSIAN -> "ru"
        LanguageMode.SYSTEM -> if (appContext?.resources?.configuration?.locales?.get(0)?.language == "ru") "ru" else "en"
    }

    private fun languageCacheKey(titleId: String): String =
        "$titleId:${sourceLanguageKey(titleId)}"

    private fun sourceLanguageKey(titleId: String): String =
        "${sourceForTitle(titleId).descriptor.id.value}:${currentLanguageKey()}"

    private fun matchesPreferredPlayer(
        candidatePlayerName: String?,
        preferredPlayerName: String?,
    ): Boolean {
        if (preferredPlayerName.isNullOrBlank()) return false
        val normalizedPreferred = preferredPlayerName.normalizePlayerName()
        val normalizedCandidate = candidatePlayerName.normalizePlayerName()
        return normalizedCandidate == normalizedPreferred ||
            normalizedCandidate.contains(normalizedPreferred) ||
            normalizedPreferred.contains(normalizedCandidate)
    }

    private fun matchesPreferredQuality(
        candidateQuality: String?,
        preferredQuality: String?,
    ): Boolean {
        return !preferredQuality.isNullOrBlank() &&
            candidateQuality?.trim()?.equals(preferredQuality.trim(), ignoreCase = true) == true
    }

    private fun String?.containsPlayerToken(token: String): Boolean =
        this.normalizePlayerName().contains(token)

    private fun String?.normalizePlayerName(): String =
        this.orEmpty().trim().lowercase()

    private fun String?.safeHost(): String {
        if (this.isNullOrBlank()) return "unknown"
        return runCatching { URI(this).host }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: "unknown"
    }

    private fun Map<String, String>.safeHeaderNames(): String {
        if (isEmpty()) return "[]"
        return keys
            .filter(String::isNotBlank)
            .sorted()
            .joinToString(prefix = "[", postfix = "]")
    }

    private fun appString(@androidx.annotation.StringRes resId: Int, vararg formatArgs: Any): String {
        val context = appContext ?: return ""
        return context.getString(resId, *formatArgs)
    }

    private fun isSupportedLink(link: PlayerLink): Boolean =
        currentExtractors().any { extractor -> extractor.supports(link) }

    /**
     * Resolver extensions can be installed while this repository is already alive.  Do not keep
     * the old list forever: otherwise a freshly installed source can return EMBED links which
     * are filtered out before its newly installed resolver ever gets a chance to handle them.
     */
    @Synchronized
    private fun currentExtractors(): List<StreamExtractor> {
        val generation = AnimeSourceRegistry.extensionGeneration
        if (extractorsGeneration == generation && activeExtractors.isNotEmpty()) return activeExtractors

        // Aniyomi extensions resolve their own embeds, so no page-specific resolvers are loaded.
        val downloadedResolvers = emptyList<StreamExtractor>()
        activeBrowserResolvers = emptyList<BrowserScriptResolver>()
        activeExtractors = buildList {
            add(DirectHlsExtractor())
            add(DirectMp4Extractor())
            appContext?.let { add(DirectStreamWebViewRelayExtractor(it)) }
            addAll(downloadedResolvers)
            appContext?.let {
                add(BrowserPlayerWebViewExtractor(it, activeBrowserResolvers, client))
            }
        }
        extractorsGeneration = generation
        return activeExtractors
    }

    private fun ensureInternetConnection() {
        val context = appContext ?: return
        if (!hasActiveInternetConnection(context)) {
            throw NoInternetConnectionException(context.getString(org.akkirrai.hibiki.R.string.home_error_no_internet))
        }
    }

    private fun StreamType.toPlaybackType(): PlaybackStreamType {
        return when (this) {
            StreamType.HLS -> PlaybackStreamType.HLS
            StreamType.MP4 -> PlaybackStreamType.MP4
            StreamType.DASH -> PlaybackStreamType.DASH
        }
    }

    private suspend fun browserPagePlayback(
        links: List<PlayerLink>,
        payload: SourcePayload,
        episode: Episode,
    ): PlaybackStream? {
        // Refresh installed resolver extensions before deciding. The resolver, not this
        // repository, carries the provider-specific host and browser action.
        currentExtractors()
        val match = links.firstNotNullOfOrNull { link ->
            activeBrowserResolvers
                .firstOrNull { it.browserPlaybackMode == BrowserPlaybackMode.PAGE && it.supportsBrowser(link) }
                ?.let { resolver -> link to resolver }
        } ?: return null
        val (link, resolver) = match
        AppLogger.d(TAG, "browser-page playback: player=${link.playerName.orEmpty()}, host=${link.url.safeHost()}")
        return PlaybackStream(
            animeTitle = payload.title.displayName,
            sourceTitle = payload.source.title,
            episodeTitle = episode.title?.takeIf(String::isNotBlank)
                ?: appString(R.string.watch_episode_fallback_title, episode.number.formatEpisodeNumber()),
            streamUrl = link.url,
            streamType = PlaybackStreamType.BROWSER,
            qualityLabel = link.quality,
            availableQualityLabels = listOfNotNull(link.quality?.trim()?.takeIf(String::isNotBlank)),
            headers = link.headers,
            browserScript = resolver.browserScript(link),
            segments = link.segments.map { it.toPlaybackSegment() },
            videoId = link.videoId,
        )
    }


    private fun selectPlaybackSegments(
        apiSegments: List<org.akkirrai.beakokit.model.VideoSegment>,
        extractedSegments: List<org.akkirrai.beakokit.model.VideoSegment>,
    ): List<org.akkirrai.beakokit.model.VideoSegment> {
        val preferred = apiSegments.ifEmpty { extractedSegments }
        return preferred
            .filter { segment -> segment.endMs > segment.startMs }
            .filter { segment -> segment.startMs >= 0L }
            .filterNot { segment ->
                segment.startMs == 0L && segment.type != org.akkirrai.beakokit.model.VideoSegmentType.UNKNOWN
            }
    }

    private fun VideoSegment.toPlaybackSegment(): PlaybackSegment = PlaybackSegment(
        type = type.toPlaybackSegmentType(),
        startMs = startMs,
        endMs = endMs,
    )

    private fun VideoSegmentType.toPlaybackSegmentType(): PlaybackSegmentType = when (this) {
        VideoSegmentType.OPENING -> PlaybackSegmentType.Opening
        VideoSegmentType.ENDING -> PlaybackSegmentType.Ending
        VideoSegmentType.UNKNOWN -> PlaybackSegmentType.Unknown
    }

    private fun Double.formatEpisodeNumber(): String {
        val asInt = toInt()
        return if (this == asInt.toDouble()) asInt.toString() else toString()
    }

    private data class CachedWatchSources(
        val sources: List<WatchSource>,
    )

    private data class CachedPlaybackStream(
        val stream: PlaybackStream,
        val playerName: String?,
        val cachedAt: Long,
    )

    private data class CachedPlayerLinks(
        val links: List<PlayerLink>,
        val cachedAt: Long,
    )

    private data class SourcePayload(
        val source: WatchSource,
        /** The source's own position in the list the extension published, which is the order
         * playback falls back through. */
        val order: Int,
        val animeId: String,
        val title: AnimeTitle,
        val group: PlaybackGroup,
        val episodes: List<Episode>,
        val runtime: AnimeSourceRuntime,
    )

    private companion object {
        const val TAG = "AnimeWatchRepository"
        const val STREAM_CACHE_TTL_MS = 10 * 60_000L
        const val PLAYER_LINKS_CACHE_TTL_MS = 60_000L
        const val UNAVAILABLE_PROVIDER_TTL_MS = 10 * 60_000L
        const val EPISODE_NUMBER_EPSILON = 0.001
        // Aniyomi extensions can resolve several host pages before returning links; their
        // OkHttp requests use a 30s read timeout, so a shorter discovery budget cancels them
        // while they are still working (observed on the YummyAnime APK source).
        const val PLAYER_LINK_DISCOVERY_TIMEOUT_MS = 45_000L
        const val FALLBACK_RESOLVE_TIMEOUT_MS = 12_000L
        const val EPISODE_RESOLUTION_TIMEOUT_MS = 60_000L
        // BROWSER-resolved players can fall back to WebViewStreamRelay when a CDN blocks a plain
        // HTTP client, which adds real WebView round-trips (JS fetch + base64 bridge) to both
        // resolution and validation - both budgets were tuned before that path existed and are too
        // tight for it, silently timing the whole attempt out with no error surfaced to the user.
        const val AUTO_RESOLVE_TIMEOUT_MS = 15_000L
        const val PREFERRED_RESOLVE_TIMEOUT_MS = 15_000L
        const val DIRECT_AUTO_RESOLVE_TIMEOUT_MS = 15_000L
        const val DIRECT_PREFERRED_RESOLVE_TIMEOUT_MS = 15_000L
    }
}

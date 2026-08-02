package org.akkirrai.hibiki.core.source

import android.content.Context
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.akkirrai.beakokit.api.StreamExtractor
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.beakokit.playback.extractor.AksorExtractor
import org.akkirrai.beakokit.playback.extractor.AniBoomExtractor
import org.akkirrai.beakokit.playback.extractor.CvhExtractor
import org.akkirrai.beakokit.playback.extractor.DirectHlsExtractor
import org.akkirrai.beakokit.playback.extractor.DirectMp4Extractor
import org.akkirrai.beakokit.playback.extractor.KodikExtractor
import org.akkirrai.beakokit.playback.extractor.SibnetExtractor
import org.akkirrai.beakokit.playback.extractor.VkExtractor
import org.akkirrai.beakokit.playback.PlaybackResolver
import org.akkirrai.beakokit.playback.validation.HttpStreamValidator
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.StreamType
import org.akkirrai.beakokit.model.VideoSegment
import org.akkirrai.beakokit.model.VideoSegmentType
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.app.settings.LanguageMode
import org.akkirrai.hibiki.core.model.PlaybackLinkOption
import org.akkirrai.hibiki.core.model.PlaybackSegment
import org.akkirrai.hibiki.core.model.PlaybackSegmentType
import org.akkirrai.hibiki.core.model.PlaybackSettingsOptions
import org.akkirrai.hibiki.core.model.PlaybackStream
import org.akkirrai.hibiki.core.model.PlaybackStreamType
import org.akkirrai.hibiki.core.model.WatchEpisode
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.model.WatchSource
import org.akkirrai.hibiki.core.network.AndroidHttpClientFactory
import org.akkirrai.hibiki.core.network.NoInternetConnectionException
import org.akkirrai.hibiki.core.network.hasActiveInternetConnection
import org.akkirrai.hibiki.shared.player.matchesPreferredPlayer
import org.akkirrai.hibiki.shared.player.matchesPreferredQuality
import org.akkirrai.hibiki.shared.player.PlayerSelectionCandidate
import org.akkirrai.hibiki.shared.player.prioritizePlayerSelection
import org.akkirrai.hibiki.shared.player.resolvePlayerAttemptTimeoutMillis
import org.akkirrai.hibiki.shared.player.formatHeaderNames
import org.akkirrai.hibiki.shared.player.resolvePlaybackStreamType
import org.akkirrai.hibiki.shared.player.resolvePlaybackSegmentType
import org.akkirrai.hibiki.shared.player.selectPlaybackSegments
import org.akkirrai.hibiki.shared.player.formatEpisodeNumber
import org.akkirrai.hibiki.shared.player.buildWatchSourceId
import org.akkirrai.hibiki.shared.player.watchTitleIdFromSourceId
import org.akkirrai.hibiki.shared.player.WatchDataRepository
import org.akkirrai.hibiki.shared.settings.resolveAppLanguageTag
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

class AnimeWatchRepository(
    context: Context? = null,
    private val client: HttpClient = AndroidHttpClientFactory.create(),
) : WatchDataRepository {
    private val cachedSources = ConcurrentHashMap<String, CachedWatchSources>()
    private val sourcePayloads = ConcurrentHashMap<String, SourcePayload>()
    private val sourcePayloadLanguages = ConcurrentHashMap<String, String>()
    private val cachedStreams = ConcurrentHashMap<String, CachedPlaybackStream>()
    private val inFlightLoads = ConcurrentHashMap<String, CompletableDeferred<List<WatchSource>>>()
    private val appContext = context?.applicationContext
    private val appPreferences = appContext?.let(::AppPreferences)
    private val sourceManager = appContext?.let { AnimeSourceRuntimeManager(it, client) }
    private val extractors = listOfNotNull<StreamExtractor>(
        DirectHlsExtractor(),
        DirectMp4Extractor(),
        AniBoomExtractor(client),
        KodikExtractor(client),
        AksorExtractor(client),
        appContext?.let(::AllohaWebViewExtractor),
        appContext?.let(::AnimePaheWebViewExtractor),
        appContext?.let(::GogoAnimeWebViewExtractor),
        SibnetExtractor(client),
        CvhExtractor(client),
        VkExtractor(client),
    )
    private val validator = HttpStreamValidator(client)
    private val playbackResolver = PlaybackResolver(extractors, validator)
    private val loadMutex = Mutex()

    fun getCachedSources(animeId: String): WatchSourcesCacheSnapshot? {
        val canonicalId = extractTitleId(animeId)
        val cached = cachedSources[languageCacheKey(canonicalId)] ?: return null
        return WatchSourcesCacheSnapshot(sources = cached.sources)
    }

    suspend fun loadSources(
        animeId: String,
        onUpdate: (List<WatchSource>) -> Unit,
        forceRefresh: Boolean = false,
    ): List<WatchSource> {
        val canonicalId = resolveAnimeId(animeId)
        val cacheKey = languageCacheKey(canonicalId)
        if (!forceRefresh) cachedSources[cacheKey]?.let {
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

    override suspend fun loadSources(animeId: String): List<WatchSource> =
        loadSources(animeId = animeId, onUpdate = {})

    override suspend fun refreshSources(animeId: String): List<WatchSource> =
        loadSources(animeId = animeId, onUpdate = {}, forceRefresh = true)

    override suspend fun getEpisodes(sourceId: String): List<WatchEpisode> {
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

    override suspend fun getPlayerLinks(sourceId: String, episodeId: String): List<PlayerLink> {
        val payload = ensureSourcePayload(sourceId)
            ?: throw IllegalArgumentException("Source is not registered: $sourceId")
        val episode = payload.episodes.firstOrNull { it.id == episodeId }
            ?: throw IllegalArgumentException("Episode is not registered: $episodeId")
        return getFilteredLinks(payload, episode)
    }

    override suspend fun resolvePlayback(
        sourceId: String,
        episodeId: String,
        preferredQuality: String?,
        preferredPlayerName: String?,
        forceRefresh: Boolean,
    ): PlaybackStream = resolveStream(
        sourceId = sourceId,
        episodeId = episodeId,
        forceRefresh = forceRefresh,
        preferredPlayerName = preferredPlayerName,
        requiredPlayerName = preferredPlayerName,
        preferredQuality = preferredQuality,
    )

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
    ): PlaybackStream {
        val cacheKey = "$sourceId:$episodeId:${preferredPlayerName.orEmpty()}:${preferredQuality.orEmpty()}:${requiredPlayerName.orEmpty()}"
        if (!forceRefresh) {
            cachedStreams[cacheKey]
                ?.takeIf { System.currentTimeMillis() - it.cachedAt < STREAM_CACHE_TTL_MS }
                ?.takeIf { it.stream.streamUrl !in excludedStreamUrls }
                ?.let { return it.stream }
        }

        ensureInternetConnection()

        val payload = ensureSourcePayload(sourceId) ?: throw SourceException(appString(R.string.watch_error_voiceover_not_found))
        val episode = payload.episodes.firstOrNull { it.id == episodeId }
            ?: throw SourceException(appString(R.string.watch_error_episode_not_found))
        val links = prioritizeLinks(
            links = getFilteredLinks(payload, episode)
                .filterNot { it.url in excludedStreamUrls },
            preferredPlayerName = preferredPlayerName,
            preferredQuality = preferredQuality,
        ).filter { link ->
            requiredPlayerName.isNullOrBlank() || matchesPreferredPlayer(link.playerName, requiredPlayerName)
        }

        if (links.isEmpty()) {
            throw SourceException(appString(R.string.watch_error_no_players))
        }

        val resolved = playbackResolver.resolve(
            links = links,
            excludedStreamUrls = excludedStreamUrls,
            preferredQuality = preferredQuality,
            attemptTimeoutMillis = { link -> resolveAttemptTimeoutMillis(preferredPlayerName, link.playerName) },
        )
        AppLogger.d(
            TAG,
            "validated stream: player=${resolved.link.playerName}, type=${resolved.validation.streamType}, " +
                "quality=${resolved.validation.quality}, status=${resolved.validation.statusCode}, " +
                "streamHost=${resolved.validation.finalUrl.safeHost()}, " +
                "headerNames=${formatHeaderNames(resolved.stream.headers)}",
        )
        val playback = PlaybackStream(
            animeTitle = payload.title.displayName,
            sourceTitle = payload.source.title,
            episodeTitle = episode.title?.takeIf(String::isNotBlank)
                ?: appString(R.string.watch_episode_fallback_title, formatEpisodeNumber(episode.number)),
            streamUrl = resolved.validation.finalUrl,
            streamType = resolvePlaybackStreamType(resolved.validation.streamType.name),
            qualityLabel = resolved.validation.quality ?: resolved.stream.quality ?: resolved.link.quality,
            availableQualityLabels = (
                resolved.availableQualityLabels + (resolved.validation.quality ?: resolved.stream.quality ?: resolved.link.quality)
            ).mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }.distinct(),
            headers = resolved.stream.headers.ifEmpty { resolved.link.headers },
            segments = selectPlaybackSegments(
                apiSegments = resolved.link.segments.map { it.toPlaybackSegment() },
                extractedSegments = resolved.stream.segments.map { it.toPlaybackSegment() },
            ),
            videoId = resolved.link.videoId,
        )
        cachedStreams[cacheKey] = CachedPlaybackStream(stream = playback, cachedAt = System.currentTimeMillis())
        return playback
    }

    suspend fun resolveFastestStream(
        sourceId: String,
        episodeId: String,
        forceRefresh: Boolean = false,
        preferredPlayerName: String? = null,
        preferredQuality: String? = null,
    ): ResolvedPlayerStream {
        val playerNames = getPlaybackSettingsOptions(sourceId, episodeId)
            .links
            .mapNotNull { it.playerName?.trim()?.takeIf(String::isNotBlank) }
            .distinctBy(String::lowercase)

        val rememberedPlayer = preferredPlayerName
            ?.takeIf { preferred -> playerNames.any { matchesPreferredPlayer(it, preferred) } }
        if (rememberedPlayer != null) {
            try {
                return ResolvedPlayerStream(
                    playerName = rememberedPlayer,
                    playback = resolveStream(
                        sourceId = sourceId,
                        episodeId = episodeId,
                        forceRefresh = forceRefresh,
                        preferredQuality = preferredQuality,
                        requiredPlayerName = rememberedPlayer,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                AppLogger.w(TAG, "Remembered download player failed; racing all players: player=$rememberedPlayer", error)
            }
        }

        if (playerNames.size <= 1) {
            val playerName = playerNames.firstOrNull()
            return ResolvedPlayerStream(
                playerName = playerName,
                playback = resolveStream(
                    sourceId = sourceId,
                    episodeId = episodeId,
                    forceRefresh = forceRefresh,
                    preferredPlayerName = playerName,
                    preferredQuality = preferredQuality,
                    requiredPlayerName = playerName,
                ),
            )
        }

        val (playerName, playback) = raceFirstSuccessful(playerNames) { candidate ->
            resolveStream(
                sourceId = sourceId,
                episodeId = episodeId,
                forceRefresh = forceRefresh,
                preferredPlayerName = candidate,
                preferredQuality = preferredQuality,
                requiredPlayerName = candidate,
            )
        }
        return ResolvedPlayerStream(playerName = playerName, playback = playback)
    }

    override suspend fun getPlaybackSettingsOptions(
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
        inFlightLoads.clear()
    }

    override fun close() {
        clearCaches()
        client.close()
    }

    private suspend fun performLoadSources(animeId: String): List<WatchSource> {
        val runtime = sourceForTitle(animeId)
        val title = runtime.details(animeId)
        if (!runtime.supportsPlayback) {
            throw SourceException(
                appString(R.string.watch_error_no_voiceovers_from_source, runtime.descriptor.name),
            )
        }
        val groups = try {
            runtime.getPlaybackGroups(title)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AppLogger.w(TAG, "${runtime.descriptor.name} source discovery failed: ${error.message}")
            throw SourceException(
                message = appString(
                    R.string.watch_error_no_voiceovers_from_source,
                    runtime.descriptor.name,
                ),
                statusCode = (error as? SourceException)?.statusCode,
                cause = error,
                kind = (error as? SourceException)?.kind ?: org.akkirrai.beakokit.api.SourceErrorKind.UNKNOWN,
            )
        }
        val sources = groups.mapIndexed { index, group ->
            val source = WatchSource(
                sourceId = buildWatchSourceId(animeId, group.title, index),
                title = group.title,
                episodeCount = group.episodes.size,
                qualityLabel = group.qualityLabel,
                isPriority = index == 0,
            )
            sourcePayloads[source.sourceId] = SourcePayload(
                source = source,
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
            throw SourceException(
                appString(R.string.watch_error_no_voiceovers_from_source, runtime.descriptor.name),
            )
        }
        return allSources
    }

    private fun sourceForTitle(titleId: String): AnimeSourceRuntime = sourceManager?.forTitle(titleId)
        ?: error("Anime source selection requires an Android context")

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
    ): List<PlayerLink> {
        return payload.runtime.getPlayerLinks(payload.title, payload.group, episode)
            .filter(::isSupportedLink)
    }

    private suspend fun resolveAnimeId(rawId: String): String {
        return sourceForTitle(rawId).normalizeId(rawId)
    }

    private fun extractTitleId(sourceId: String): String {
        return watchTitleIdFromSourceId(sourceId)
    }

    internal fun prioritizeLinks(
        links: List<PlayerLink>,
        preferredPlayerName: String?,
        preferredQuality: String?,
    ): List<PlayerLink> {
        val order = prioritizePlayerSelection(
            candidates = links.mapIndexed { index, link ->
                PlayerSelectionCandidate(index, link.playerName, link.quality)
            },
            preferredPlayerName = preferredPlayerName,
            preferredQuality = preferredQuality,
        )
        return order.map(links::get)
    }

    internal fun resolveAttemptTimeoutMillis(
        preferredPlayerName: String?,
        candidatePlayerName: String?,
    ): Long = resolvePlayerAttemptTimeoutMillis(
        preferredPlayerName = preferredPlayerName,
        candidatePlayerName = candidatePlayerName,
        preferredTimeoutMs = PREFERRED_RESOLVE_TIMEOUT_MS,
        automaticTimeoutMs = AUTO_RESOLVE_TIMEOUT_MS,
    )

    private fun currentLanguageKey(): String = resolveAppLanguageTag(
        appPreferences?.state?.value?.languageMode ?: LanguageMode.SYSTEM,
        appContext?.resources?.configuration?.locales?.get(0)?.language.orEmpty(),
    )

    private fun languageCacheKey(titleId: String): String =
        "$titleId:${sourceLanguageKey(titleId)}"

    private fun sourceLanguageKey(titleId: String): String =
        "${sourceForTitle(titleId).descriptor.id.value}:${currentLanguageKey()}"

    private fun String?.safeHost(): String {
        if (this.isNullOrBlank()) return "unknown"
        return runCatching { URI(this).host }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: "unknown"
    }

    private fun appString(@androidx.annotation.StringRes resId: Int, vararg formatArgs: Any): String {
        val context = appContext ?: return ""
        return context.getString(resId, *formatArgs)
    }

    private fun isSupportedLink(link: PlayerLink): Boolean =
        extractors.any { extractor -> extractor.supports(link) }

    private fun ensureInternetConnection() {
        val context = appContext ?: return
        if (!hasActiveInternetConnection(context)) {
            throw NoInternetConnectionException(context.getString(org.akkirrai.hibiki.R.string.home_error_no_internet))
        }
    }

    private fun VideoSegment.toPlaybackSegment(): PlaybackSegment = PlaybackSegment(
        type = resolvePlaybackSegmentType(type.name),
        startMs = startMs,
        endMs = endMs,
    )

    private data class CachedWatchSources(
        val sources: List<WatchSource>,
    )

    private data class CachedPlaybackStream(
        val stream: PlaybackStream,
        val cachedAt: Long,
    )

    private data class SourcePayload(
        val source: WatchSource,
        val animeId: String,
        val title: AnimeTitle,
        val group: PlaybackGroup,
        val episodes: List<Episode>,
        val runtime: AnimeSourceRuntime,
    )

    private companion object {
        const val TAG = "AnimeWatchRepository"
        const val STREAM_CACHE_TTL_MS = 10 * 60_000L
        const val AUTO_RESOLVE_TIMEOUT_MS = 8_000L
        const val PREFERRED_RESOLVE_TIMEOUT_MS = 12_000L
    }
}

internal suspend fun <Candidate, Value> raceFirstSuccessful(
    candidates: List<Candidate>,
    attempt: suspend (Candidate) -> Value,
): Pair<Candidate, Value> = supervisorScope {
    require(candidates.isNotEmpty())
    val results = Channel<Result<Pair<Candidate, Value>>>(capacity = candidates.size)
    val jobs = candidates.map { candidate ->
        launch {
            val result = try {
                Result.success(candidate to attempt(candidate))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Result.failure(error)
            }
            results.send(result)
        }
    }

    var firstError: Throwable? = null
    repeat(candidates.size) {
        val result = results.receive()
        result.getOrNull()?.let { resolved ->
            jobs.forEach { it.cancel() }
            return@supervisorScope resolved
        }
        if (firstError == null) firstError = result.exceptionOrNull()
    }

    results.close()
    throw firstError ?: IllegalStateException("No candidate completed successfully")
}

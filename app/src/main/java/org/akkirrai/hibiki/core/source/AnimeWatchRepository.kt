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
import org.akkirrai.animeresolver.core.PlayerExtractor
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.animeresolver.extractor.AksorExtractor
import org.akkirrai.animeresolver.extractor.AniBoomExtractor
import org.akkirrai.animeresolver.extractor.CvhExtractor
import org.akkirrai.animeresolver.extractor.DirectHlsExtractor
import org.akkirrai.animeresolver.extractor.DirectMp4Extractor
import org.akkirrai.animeresolver.extractor.KodikExtractor
import org.akkirrai.animeresolver.extractor.SibnetExtractor
import org.akkirrai.animeresolver.extractor.VkExtractor
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.StreamType
import org.akkirrai.beakokit.model.VideoSegment
import org.akkirrai.beakokit.model.VideoSegmentType
import org.akkirrai.animeresolver.validator.HttpStreamValidator
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
) {
    private val cachedSources = ConcurrentHashMap<String, CachedWatchSources>()
    private val sourcePayloads = ConcurrentHashMap<String, SourcePayload>()
    private val sourcePayloadLanguages = ConcurrentHashMap<String, String>()
    private val cachedStreams = ConcurrentHashMap<String, CachedPlaybackStream>()
    private val inFlightLoads = ConcurrentHashMap<String, CompletableDeferred<List<WatchSource>>>()
    private val appContext = context?.applicationContext
    private val appPreferences = appContext?.let(::AppPreferences)
    private val sourceManager = appContext?.let { AnimeSourceRuntimeManager(it, client) }
    private val extractors = listOfNotNull<PlayerExtractor>(
        DirectHlsExtractor(),
        DirectMp4Extractor(),
        AniBoomExtractor(client),
        KodikExtractor(client),
        AksorExtractor(client),
        appContext?.let(::AllohaWebViewExtractor),
        SibnetExtractor(client),
        CvhExtractor(client),
        VkExtractor(client),
    )
    private val validator = HttpStreamValidator(client)
    private val loadMutex = Mutex()

    fun getCachedSources(animeId: String): WatchSourcesCacheSnapshot? {
        val canonicalId = extractTitleId(animeId)
        val cached = cachedSources[languageCacheKey(canonicalId)] ?: return null
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

        val errors = mutableListOf<String>()
        for (link in links) {
            val extractor = extractors.firstOrNull { it.supports(link) }
            if (extractor == null) {
                errors += appString(
                    R.string.watch_error_extractor_missing,
                    link.playerName ?: appString(R.string.watch_player_fallback_name),
                )
                continue
            }

            val playback = runCatching {
                withTimeout(resolveAttemptTimeoutMillis(preferredPlayerName, link.playerName)) {
                    val streams = extractor.extractVariants(link)
                        .filterNot { it.url in excludedStreamUrls }
                        .sortedWith(
                            compareByDescending<org.akkirrai.beakokit.model.VideoStream> {
                                matchesPreferredQuality(it.quality, preferredQuality)
                            }.thenByDescending {
                                it.quality?.filter(Char::isDigit)?.toIntOrNull() ?: 0
                            }
                        )
                    if (streams.isEmpty()) {
                        throw SourceException(appString(R.string.watch_error_stream_unavailable))
                    }
                    val availableQualityLabels = streams
                        .mapNotNull { it.quality?.trim()?.takeIf(String::isNotBlank) }
                        .distinct()
                    streams.firstNotNullOfOrNull { stream ->
                        val validation = validator.validate(stream)
                        AppLogger.d(
                            TAG,
                            buildString {
                                append("validate stream: player=")
                                append(link.playerName)
                                append(", type=")
                                append(stream.type)
                                append(", quality=")
                                append(stream.quality)
                                append(", success=")
                                append(validation.success)
                                append(", status=")
                                append(validation.statusCode)
                                append(", streamHost=")
                                append(validation.finalUrl.safeHost())
                                append(", headerNames=")
                                append(stream.headers.safeHeaderNames())
                                append(", message=")
                                append(validation.message)
                            },
                        )
                        if (!validation.success) return@firstNotNullOfOrNull null
                        PlaybackStream(
                            animeTitle = payload.title.displayName,
                            sourceTitle = payload.source.title,
                            episodeTitle = episode.title?.takeIf(String::isNotBlank)
                                ?: appString(R.string.watch_episode_fallback_title, episode.number.formatEpisodeNumber()),
                            streamUrl = validation.finalUrl,
                            streamType = validation.streamType.toPlaybackType(),
                            qualityLabel = validation.quality ?: stream.quality ?: link.quality,
                            availableQualityLabels = (
                                availableQualityLabels + (validation.quality ?: stream.quality ?: link.quality)
                            ).mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }.distinct(),
                            headers = stream.headers.ifEmpty { link.headers },
                            segments = selectPlaybackSegments(
                                apiSegments = link.segments,
                                extractedSegments = stream.segments,
                            ).map { segment -> segment.toPlaybackSegment() },
                            videoId = link.videoId,
                        )
                    } ?: throw SourceException(appString(R.string.watch_error_stream_unavailable))
                }
            }.getOrElse { error ->
                errors += when (error) {
                    is TimeoutCancellationException -> appString(
                        R.string.watch_error_timeout,
                        link.playerName ?: appString(R.string.watch_player_fallback_name),
                    )
                    is CancellationException -> throw error
                    else -> error.message ?: appString(R.string.watch_error_open_player_failed)
                }
                null
            }

            if (playback != null) {
                cachedStreams[cacheKey] = CachedPlaybackStream(
                    stream = playback,
                    cachedAt = System.currentTimeMillis(),
                )
                return playback
            }
        }

        throw SourceException(errors.firstOrNull() ?: appString(R.string.watch_error_stream_unavailable))
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
        inFlightLoads.clear()
    }

    fun close() {
        clearCaches()
        client.close()
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
    ): Long {
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
        extractors.any { extractor -> extractor.supports(link) }

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

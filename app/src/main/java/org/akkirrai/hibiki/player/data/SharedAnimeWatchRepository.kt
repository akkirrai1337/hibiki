package org.akkirrai.hibiki.player

import io.ktor.client.HttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.akkirrai.beakokit.api.AnimeKey
import org.akkirrai.beakokit.api.AnimeSource
import org.akkirrai.beakokit.api.ChallengeSessionProvider
import org.akkirrai.beakokit.api.DefaultSourceContext
import org.akkirrai.beakokit.api.HealthTrackingSourceExecutionPolicy
import org.akkirrai.beakokit.api.PlaybackGroup
import org.akkirrai.beakokit.api.PlaybackSource
import org.akkirrai.beakokit.api.SourceExecutionPolicy
import org.akkirrai.beakokit.api.SourceConfig
import org.akkirrai.beakokit.api.SourceHealthReporter
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.SourceLogger
import org.akkirrai.beakokit.api.SourceUnavailableException
import org.akkirrai.beakokit.api.StreamExtractor
import org.akkirrai.beakokit.api.RuntimeBackedPlaybackAnimeSource
import org.akkirrai.beakokit.http.BeakoKitHttpPolicy
import org.akkirrai.beakokit.http.installBeakoKitHttpDefaults
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.playback.PlaybackResolver
import org.akkirrai.beakokit.playback.commonPlaybackExtractors
import org.akkirrai.beakokit.playback.validation.HttpStreamValidator
import org.akkirrai.hibiki.player.model.PlaybackStream
import org.akkirrai.hibiki.player.model.PlaybackLinkOption
import org.akkirrai.hibiki.player.model.PlaybackSettingsOptions
import org.akkirrai.hibiki.player.model.WatchEpisode
import org.akkirrai.hibiki.player.model.WatchSource
import kotlin.time.Clock

/** Shared source/runtime adapter used by non-Android hosts. */
class SharedAnimeWatchRepository(
    private val client: HttpClient,
    private val sourceHttpClient: HttpClient = client,
    private val preferEnglish: Boolean = false,
    private val externalSourceFactory: ((SourceId, DefaultSourceContext) -> AnimeSource?)? = null,
    private val sourceConfigProvider: (SourceId) -> SourceConfig = { SourceConfig.EMPTY },
    private val playbackAttemptTimeoutMillis: Long = DEFAULT_PLAYBACK_ATTEMPT_TIMEOUT_MILLIS,
    private val sourceHealthReporter: SourceHealthReporter = SourceHealthReporter.NONE,
    private val sourceExecutionPolicy: SourceExecutionPolicy =
        HealthTrackingSourceExecutionPolicy(sourceHealthReporter),
    private val challengeSessionProvider: ChallengeSessionProvider = ChallengeSessionProvider.UNSUPPORTED,
    private val additionalExtractors: List<StreamExtractor> = emptyList(),
) : WatchDataRepository {
    private val playbackExtractors = commonPlaybackExtractors(client) + additionalExtractors
    private val playbackResolver = PlaybackResolver(
        extractors = playbackExtractors,
        validator = HttpStreamValidator(client),
    )
    private val sources = mutableMapOf<SourceId, AnimeSource>()
    private val payloads = mutableMapOf<String, WatchPayload>()
    private val cachedSources = mutableMapOf<String, List<WatchSource>>()
    private val resolvedPlayback = TimedPlaybackCache<String, PlaybackStream>(
        ttlMillis = PLAYBACK_CACHE_TTL_MILLIS,
        nowMillis = { Clock.System.now().toEpochMilliseconds() },
    )

    override suspend fun loadSources(animeId: String): List<WatchSource> {
        cachedSources[animeId]?.let { return it }
        resolvedPlayback.clear()
        val titleKey = AnimeKey.parse(animeId)
            ?: throw IllegalArgumentException("Invalid anime id: $animeId")
        val loaded = withContext(Dispatchers.Default) {
            val source = sourceFor(titleKey.sourceId)
            val playback = source as? PlaybackSource
                ?: throw SourceUnavailableException(
                    "Source does not support playback: ${titleKey.sourceId.value}",
                )
            if (source is RuntimeBackedPlaybackAnimeSource) {
                coroutineScope {
                    val title = async { source.getById(titleKey.nativeId) }
                    val groups = async {
                        playback.getPlaybackGroups(
                            AnimeTitle(
                                id = titleKey.nativeId,
                                russianName = null,
                                englishName = null,
                                originalName = titleKey.nativeId,
                                japaneseName = null,
                                synonyms = emptyList(),
                                year = null,
                                type = null,
                                episodeCount = null,
                                posterUrl = null,
                                status = null,
                                description = null,
                            ),
                        )
                    }
                    title.await() to (playback to groups.await())
                }
            } else {
                val title = source.getById(titleKey.nativeId)
                title to (playback to playback.getPlaybackGroups(title))
            }
        }
        val title = loaded.first
        val playback = loaded.second.first
        val groups = loaded.second.second
        if (groups.isEmpty()) {
            throw SourceUnavailableException(
                "Source returned no playback groups: ${titleKey.sourceId.value}",
            )
        }
        val result = groups.mapIndexed { index, group ->
            val watchSource = WatchSource(
                sourceId = buildWatchSourceId(animeId, group.title, index),
                title = group.title,
                episodeCount = group.episodes.size,
                qualityLabel = group.qualityLabel,
                isPriority = index == 0,
            )
            payloads[watchSource.sourceId] = WatchPayload(
                source = watchSource,
                title = title,
                group = group,
                playback = playback,
            )
            watchSource
        }
        cachedSources[animeId] = result
        return result
    }

    override suspend fun refreshSources(animeId: String): List<WatchSource> {
        cachedSources.remove(animeId)
        payloads.keys.removeAll { watchTitleIdFromSourceId(it) == animeId }
        return loadSources(animeId)
    }

    override suspend fun getEpisodes(sourceId: String): List<WatchEpisode> = payloadFor(sourceId)
        .group.episodes
        .sortedBy(Episode::number)
        .map { episode -> WatchEpisode(episode.id, episode.number, episode.title) }

    override suspend fun getPlayerLinks(sourceId: String, episodeId: String): List<PlayerLink> {
        val payload = payloadFor(sourceId)
        val episode = payload.group.episodes.firstOrNull { it.id == episodeId }
            ?: throw IllegalArgumentException("Episode is not registered: $episodeId")
        return withContext(Dispatchers.Default) {
            payload.playback.getPlayerLinks(payload.title, payload.group, episode)
        }
    }

    override suspend fun getPlaybackSettingsOptions(
        sourceId: String,
        episodeId: String,
    ): PlaybackSettingsOptions {
        val payload = payloadFor(sourceId)
        val episode = payload.group.episodes.firstOrNull { it.id == episodeId }
            ?: throw IllegalArgumentException("Episode is not registered: $episodeId")
        val voiceovers = payloads.values
            .filter { it.title == payload.title }
            .map(WatchPayload::source)
            .distinctBy(WatchSource::sourceId)
        val links = withContext(Dispatchers.Default) {
            payload.playback.getPlayerLinks(payload.title, payload.group, episode)
        }
            .map { link -> PlaybackLinkOption(link.playerName, link.quality) }
            .distinct()
        return PlaybackSettingsOptions(voiceovers = voiceovers, links = links)
    }

    override suspend fun resolvePlayback(
        sourceId: String,
        episodeId: String,
        preferredQuality: String?,
        preferredPlayerName: String?,
        forceRefresh: Boolean,
    ): PlaybackStream {
        val cacheKey = listOf(sourceId, episodeId, preferredPlayerName.orEmpty(), preferredQuality.orEmpty()).joinToString("|")
        if (!forceRefresh) {
            resolvedPlayback.get(cacheKey)?.let { return it }
        }
        val payload = payloadFor(sourceId)
        val episode = payload.group.episodes.firstOrNull { it.id == episodeId }
            ?: throw IllegalArgumentException("Episode is not registered: $episodeId")
        val links = withContext(Dispatchers.Default) {
            payload.playback.getPlayerLinks(payload.title, payload.group, episode)
        }
        val prioritizedLinks = selectPlaybackLinks(
            links = links,
            supports = { link -> playbackExtractors.any { extractor -> extractor.supports(link) } },
            preferredPlayerName = preferredPlayerName,
            preferredQuality = preferredQuality,
        )
        val playback = playbackResolver.resolve(
            links = prioritizedLinks,
            preferredQuality = preferredQuality,
            attemptTimeoutMillis = { playbackAttemptTimeoutMillis },
        ).toPlaybackStream(
            animeTitle = payload.title.displayName,
            sourceTitle = payload.source.title,
            episodeTitle = episode.title ?: "Episode ${episode.number}",
        )
        resolvedPlayback.put(cacheKey, playback)
        return playback
    }

    /** Drops cached external source state after its host-provided configuration changes. */
    fun invalidateSource(sourceId: SourceId) {
        sources.remove(sourceId)
        cachedSources.keys.removeAll { AnimeKey.parse(it)?.sourceId == sourceId }
        payloads.clear()
        resolvedPlayback.clear()
    }

    override fun close() {
        cachedSources.clear()
        payloads.clear()
        resolvedPlayback.clear()
        sources.clear()
        client.close()
    }

    private suspend fun payloadFor(sourceId: String): WatchPayload {
        payloads[sourceId]?.let { return it }

        val titleId = watchTitleIdFromSourceId(sourceId)
        if (titleId != sourceId) loadSources(titleId)

        return payloads[sourceId]
            ?: error("Watch source is not loaded: $sourceId")
    }

    private fun sourceFor(sourceId: SourceId): AnimeSource = sources.getOrPut(sourceId) {
        val context = DefaultSourceContext(
                httpClient = sourceHttpClient,
                preferredLanguages = if (preferEnglish) {
                    listOf(SourceLanguage.ENGLISH, SourceLanguage.RUSSIAN)
                } else {
                    listOf(SourceLanguage.RUSSIAN, SourceLanguage.ENGLISH)
                },
                config = sourceConfigProvider(sourceId),
                sourceHealthReporter = sourceHealthReporter,
                sourceExecutionPolicy = sourceExecutionPolicy,
                challengeSessionProvider = challengeSessionProvider,
                logger = SourceLogger { level, message, throwable ->
                    val suffix = throwable?.message?.takeIf(String::isNotBlank)?.let { " ($it)" }.orEmpty()
                    println("BeakoKit/${sourceId.value} [$level] $message$suffix")
                },
            )
        externalSourceFactory?.invoke(sourceId, context)
            ?: error("No external source available for $sourceId")
    }

    private data class WatchPayload(
        val source: WatchSource,
        val title: AnimeTitle,
        val group: PlaybackGroup,
        val playback: PlaybackSource,
    )

    private companion object {
        const val DEFAULT_PLAYBACK_ATTEMPT_TIMEOUT_MILLIS = 8_000L
        const val PLAYBACK_CACHE_TTL_MILLIS = 5 * 60 * 1_000L
    }
}

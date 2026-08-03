package org.akkirrai.hibiki.shared.player

import io.ktor.client.HttpClient
import org.akkirrai.beakokit.api.AnimeKey
import org.akkirrai.beakokit.api.AnimeSource
import org.akkirrai.beakokit.api.DefaultSourceContext
import org.akkirrai.beakokit.api.HealthTrackingSourceExecutionPolicy
import org.akkirrai.beakokit.api.MapSourceConfig
import org.akkirrai.beakokit.api.PlaybackGroup
import org.akkirrai.beakokit.api.PlaybackSource
import org.akkirrai.beakokit.api.SourceExecutionPolicy
import org.akkirrai.beakokit.api.SourceConfig
import org.akkirrai.beakokit.api.SourceHealthReporter
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.SourceLogger
import org.akkirrai.beakokit.api.SourceUnavailableException
import org.akkirrai.beakokit.http.BeakoKitHttpPolicy
import org.akkirrai.beakokit.http.installBeakoKitHttpDefaults
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.playback.PlaybackResolver
import org.akkirrai.beakokit.playback.commonPlaybackExtractors
import org.akkirrai.beakokit.playback.validation.HttpStreamValidator
import org.akkirrai.beakokit.source.BuiltInSources
import org.akkirrai.beakokit.source.yummy.YummyAnimeConfig
import org.akkirrai.hibiki.shared.model.PlaybackStream
import org.akkirrai.hibiki.shared.model.PlaybackLinkOption
import org.akkirrai.hibiki.shared.model.PlaybackSettingsOptions
import org.akkirrai.hibiki.shared.model.WatchEpisode
import org.akkirrai.hibiki.shared.model.WatchSource
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
) : WatchDataRepository {
    private val playbackExtractors = commonPlaybackExtractors(client)
    private val playbackResolver = PlaybackResolver(
        extractors = playbackExtractors,
        validator = HttpStreamValidator(client),
    )
    private val sources = mutableMapOf<SourceId, AnimeSource>()
    private val payloads = mutableMapOf<String, WatchPayload>()
    private val resolvedPlayback = TimedPlaybackCache<String, PlaybackStream>(
        ttlMillis = PLAYBACK_CACHE_TTL_MILLIS,
        nowMillis = { Clock.System.now().toEpochMilliseconds() },
    )

    override suspend fun loadSources(animeId: String): List<WatchSource> {
        resolvedPlayback.clear()
        val titleKey = AnimeKey.parse(animeId)
            ?: throw IllegalArgumentException("Invalid anime id: $animeId")
        val source = sourceFor(titleKey.sourceId)
        val title = source.getById(titleKey.nativeId)
        val playback = source as? PlaybackSource
            ?: throw SourceUnavailableException(
                "Source does not support playback: ${titleKey.sourceId.value}",
            )
        val groups = playback.getPlaybackGroups(title)
        if (groups.isEmpty()) {
            throw SourceUnavailableException(
                "Source returned no playback groups: ${titleKey.sourceId.value}",
            )
        }
        return groups.mapIndexed { index, group ->
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
    }

    override suspend fun getEpisodes(sourceId: String): List<WatchEpisode> = payloadFor(sourceId)
        .group.episodes
        .sortedBy(Episode::number)
        .map { episode -> WatchEpisode(episode.id, episode.number, episode.title) }

    override suspend fun getPlayerLinks(sourceId: String, episodeId: String): List<PlayerLink> {
        val payload = payloadFor(sourceId)
        val episode = payload.group.episodes.firstOrNull { it.id == episodeId }
            ?: throw IllegalArgumentException("Episode is not registered: $episodeId")
        return payload.playback.getPlayerLinks(payload.title, payload.group, episode)
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
        val links = payload.playback.getPlayerLinks(payload.title, payload.group, episode)
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
        val prioritizedLinks = selectPlaybackLinks(
            links = payload.playback.getPlayerLinks(payload.title, payload.group, episode),
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

    override fun close() {
        payloads.clear()
        resolvedPlayback.clear()
        sources.clear()
        client.close()
    }

    private fun payloadFor(sourceId: String): WatchPayload = payloads[sourceId]
        ?: error("Watch source is not loaded: $sourceId")

    private fun sourceFor(sourceId: SourceId): AnimeSource = sources.getOrPut(sourceId) {
        val context = DefaultSourceContext(
                httpClient = sourceHttpClient,
                preferredLanguages = if (preferEnglish) {
                    listOf(SourceLanguage.ENGLISH, SourceLanguage.RUSSIAN)
                } else {
                    listOf(SourceLanguage.RUSSIAN, SourceLanguage.ENGLISH)
                },
                config = when (sourceId) {
                    BuiltInSources.YUMMY_ANIME_ID -> MapSourceConfig(
                        secrets = mapOf(YummyAnimeConfig.APPLICATION_TOKEN to DEFAULT_YUMMY_APPLICATION_TOKEN),
                    )
                    else -> sourceConfigProvider(sourceId)
                },
                sourceHealthReporter = sourceHealthReporter,
                sourceExecutionPolicy = sourceExecutionPolicy,
                logger = SourceLogger { level, message, throwable ->
                    val suffix = throwable?.message?.takeIf(String::isNotBlank)?.let { " ($it)" }.orEmpty()
                    println("BeakoKit/${sourceId.value} [$level] $message$suffix")
                },
            )
        externalSourceFactory?.invoke(sourceId, context)
            ?: BuiltInSources.catalog.create(sourceId, context)
    }

    private data class WatchPayload(
        val source: WatchSource,
        val title: AnimeTitle,
        val group: PlaybackGroup,
        val playback: PlaybackSource,
    )

    private companion object {
        const val DEFAULT_YUMMY_APPLICATION_TOKEN = "wawegr8j13it4rdw"
        const val DEFAULT_PLAYBACK_ATTEMPT_TIMEOUT_MILLIS = 8_000L
        const val PLAYBACK_CACHE_TTL_MILLIS = 5 * 60 * 1_000L
    }
}

package org.akkirrai.hibiki.shared.player

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import org.akkirrai.beakokit.api.AnimeKey
import org.akkirrai.beakokit.api.AnimeSource
import org.akkirrai.beakokit.api.DefaultSourceContext
import org.akkirrai.beakokit.api.HealthTrackingSourceExecutionPolicy
import org.akkirrai.beakokit.api.MapSourceConfig
import org.akkirrai.beakokit.api.PlaybackGroup
import org.akkirrai.beakokit.api.PlaybackSource
import org.akkirrai.beakokit.api.SourceExecutionPolicy
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
import org.akkirrai.hibiki.shared.model.WatchEpisode
import org.akkirrai.hibiki.shared.model.WatchSource
import org.akkirrai.hibiki.shared.model.PlaybackStream
import org.akkirrai.hibiki.shared.player.selectPlaybackLinks
import org.akkirrai.hibiki.shared.player.toPlaybackStream
import kotlin.time.Clock

/** iOS playback source bridge; it exposes the same source-owned contracts as Android. */
internal class IosAnimeWatchRepository(
    private val preferEnglish: Boolean = false,
    private val sourceHealthReporter: SourceHealthReporter = SourceHealthReporter.NONE,
    private val sourceExecutionPolicy: SourceExecutionPolicy =
        HealthTrackingSourceExecutionPolicy(sourceHealthReporter),
) : WatchDataRepository {
    private val client = HttpClient(Darwin) {
        installBeakoKitHttpDefaults(
            BeakoKitHttpPolicy(userAgent = "Hibiki/0.1 iOS"),
        )
    }
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
        return result
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

    override suspend fun resolvePlayback(
        sourceId: String,
        episodeId: String,
        preferredQuality: String?,
    ): PlaybackStream {
        val cacheKey = playbackCacheKey(sourceId, episodeId, preferredQuality)
        resolvedPlayback.get(cacheKey)?.let {
            logPlayback("cache-hit", sourceId, episodeId, it)
            return it
        }
        val payload = payloadFor(sourceId)
        val episode = payload.group.episodes.firstOrNull { it.id == episodeId }
            ?: throw IllegalArgumentException("Episode is not registered: $episodeId")
        val prioritizedLinks = selectPlaybackLinks(
            links = payload.playback.getPlayerLinks(payload.title, payload.group, episode),
            supports = { link -> playbackExtractors.any { extractor -> extractor.supports(link) } },
            preferredPlayerName = null,
            preferredQuality = preferredQuality,
        )
        val resolved = playbackResolver.resolve(
            links = prioritizedLinks,
            preferredQuality = preferredQuality,
        )
        val playback = resolved.toPlaybackStream(
            animeTitle = payload.title.displayName,
            sourceTitle = payload.source.title,
            episodeTitle = episode.title ?: "Episode ${episode.number}",
        )
        resolvedPlayback.put(cacheKey, playback)
        logPlayback("resolved", sourceId, episodeId, playback)
        return playback
    }

    fun getCachedSources(animeId: String): List<WatchSource> = payloads.values
        .filter { watchTitleIdFromSourceId(it.source.sourceId) == animeId }
        .map(WatchPayload::source)

    override fun close() {
        payloads.clear()
        resolvedPlayback.clear()
        sources.clear()
        client.close()
    }

    private fun payloadFor(sourceId: String): WatchPayload = payloads[sourceId]
        ?: error("Watch source is not loaded: $sourceId")

    private fun playbackCacheKey(sourceId: String, episodeId: String, preferredQuality: String?): String =
        listOf(sourceId, episodeId, preferredQuality.orEmpty()).joinToString("|")

    private fun logPlayback(stage: String, sourceId: String, episodeId: String, playback: PlaybackStream) {
        val host = playback.streamUrl.substringAfter("://", playback.streamUrl).substringBefore('/')
        println(
            "Hibiki playback $stage: source=$sourceId episode=$episodeId " +
                "type=${playback.streamType} quality=${playback.qualityLabel ?: "unknown"} host=$host " +
                "headers=${playback.headers.keys.sorted().joinToString(",")}",
        )
    }

    private fun sourceFor(sourceId: SourceId): AnimeSource = sources.getOrPut(sourceId) {
        BuiltInSources.catalog.create(
            sourceId,
            DefaultSourceContext(
                httpClient = client,
                preferredLanguages = if (preferEnglish) {
                    listOf(SourceLanguage.ENGLISH, SourceLanguage.RUSSIAN)
                } else {
                    listOf(SourceLanguage.RUSSIAN, SourceLanguage.ENGLISH)
                },
                config = sourceConfig(sourceId),
                sourceHealthReporter = sourceHealthReporter,
                sourceExecutionPolicy = sourceExecutionPolicy,
                logger = SourceLogger { level, message, throwable ->
                    val suffix = throwable?.message?.takeIf(String::isNotBlank)?.let { " ($it)" }.orEmpty()
                    println("BeakoKit/${sourceId.value} [$level] $message$suffix")
                },
            ),
        )
    }

    private fun sourceConfig(sourceId: SourceId) = when (sourceId) {
        BuiltInSources.YUMMY_ANIME_ID -> MapSourceConfig(
            secrets = mapOf(YummyAnimeConfig.APPLICATION_TOKEN to DEFAULT_YUMMY_APPLICATION_TOKEN),
        )
        else -> MapSourceConfig()
    }

    private data class WatchPayload(
        val source: WatchSource,
        val title: AnimeTitle,
        val group: PlaybackGroup,
        val playback: PlaybackSource,
    )

    private companion object {
        const val DEFAULT_YUMMY_APPLICATION_TOKEN = "wawegr8j13it4rdw"
        const val PLAYBACK_CACHE_TTL_MILLIS = 5 * 60 * 1_000L
    }
}

package org.akkirrai.hibiki.shared.model

data class WatchSource(
    val sourceId: String,
    val title: String,
    val episodeCount: Int?,
    val qualityLabel: String? = null,
    val isPriority: Boolean = false,
)

data class WatchEpisode(val id: String, val number: Double, val title: String?)

data class PlaybackStream(
    val animeTitle: String,
    val sourceTitle: String,
    val episodeTitle: String,
    val streamUrl: String,
    val streamType: PlaybackStreamType,
    val qualityLabel: String? = null,
    val availableQualityLabels: List<String> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val segments: List<PlaybackSegment> = emptyList(),
    val videoId: Long? = null,
)

data class PlaybackSegment(val type: PlaybackSegmentType, val startMs: Long, val endMs: Long)

enum class PlaybackSegmentType { Opening, Ending, Unknown }

data class PlaybackLinkOption(val playerName: String?, val qualityLabel: String?)

data class PlaybackSettingsOptions(
    val voiceovers: List<WatchSource> = emptyList(),
    val links: List<PlaybackLinkOption> = emptyList(),
)

enum class PlaybackStreamType { HLS, MP4, DASH }

data class TitleWatchState(
    val titleId: String,
    val episodeId: String,
    val episodeNumber: Double,
    val sourceId: String,
    val voiceoverId: String,
    val sourceTitle: String,
    val quality: String? = null,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

data class EpisodeWatchProgress(
    val titleId: String,
    val episodeId: String,
    val episodeNumber: Double,
    val sourceId: String,
    val voiceoverId: String,
    val sourceTitle: String,
    val quality: String? = null,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

data class WatchSourceSelection(
    val titleId: String,
    val sourceId: String?,
    val sourceTitle: String?,
    val quality: String? = null,
    val playerName: String? = null,
    val autoSelect: Boolean = true,
)

enum class EpisodeProgressStatus { NotStarted, InProgress, Watched }

package eu.kanade.tachiyomi.animesource.model

import android.net.Uri
import kotlinx.serialization.json.JsonObject
import okhttp3.Headers

data class Track(val url: String, val lang: String)

enum class ChapterType {
    Opening,
    Ending,
    Recap,
    MixedOp,
    Other,
}

data class TimeStamp(
    val start: Double,
    val end: Double,
    val name: String,
    val type: ChapterType = ChapterType.Other,
)

/** API v16 video model with the deprecated v14 constructor/accessors retained for old APKs. */
@Suppress("unused_parameter")
data class Video(
    val videoUrl: String = "",
    val videoTitle: String = "",
    val resolution: Int? = null,
    val bitrate: Int? = null,
    val headers: Headers? = null,
    val preferred: Boolean = false,
    val subtitleTracks: List<Track> = emptyList(),
    val audioTracks: List<Track> = emptyList(),
    val timestamps: List<TimeStamp> = emptyList(),
    val mpvArgs: List<Pair<String, String>> = emptyList(),
    val ffmpegStreamArgs: List<Pair<String, String>> = emptyList(),
    val ffmpegVideoArgs: List<Pair<String, String>> = emptyList(),
    val internalData: String = "",
    val initialized: Boolean = false,
) {
    /** Original v14 fields, still consumed by older extensions and Hibiki's adapter. */
    private var legacyUrl: String? = null

    /** extensions-lib 17 adds a trailing JsonObject of extension-side extras; Hibiki ignores it. */
    constructor(
        videoUrl: String = "",
        videoTitle: String = "",
        resolution: Int? = null,
        bitrate: Int? = null,
        headers: Headers? = null,
        preferred: Boolean = false,
        subtitleTracks: List<Track> = emptyList(),
        audioTracks: List<Track> = emptyList(),
        timestamps: List<TimeStamp> = emptyList(),
        mpvArgs: List<Pair<String, String>> = emptyList(),
        ffmpegStreamArgs: List<Pair<String, String>> = emptyList(),
        ffmpegVideoArgs: List<Pair<String, String>> = emptyList(),
        internalData: String = "",
        initialized: Boolean = false,
        extras: JsonObject?,
    ) : this(
        videoUrl, videoTitle, resolution, bitrate, headers, preferred, subtitleTracks, audioTracks,
        timestamps, mpvArgs, ffmpegStreamArgs, ffmpegVideoArgs, internalData, initialized,
    )

    val url: String
        get() = legacyUrl ?: videoUrl

    val quality: String
        get() = videoTitle

    @Deprecated(
        message = "Use the new Video constructor",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith(
            "Video(videoTitle = quality, videoUrl = videoUrl, headers = headers, subtitleTracks = subtitleTracks, audioTracks = audioTracks)",
        ),
    )
    constructor(
        url: String,
        quality: String,
        videoUrl: String?,
        headers: Headers? = null,
        subtitleTracks: List<Track> = emptyList(),
        audioTracks: List<Track> = emptyList(),
    ) : this(
        videoUrl = videoUrl.orEmpty(),
        videoTitle = quality,
        headers = headers,
        subtitleTracks = subtitleTracks,
        audioTracks = audioTracks,
    ) {
        legacyUrl = url
    }

    @Deprecated("Use the new Video constructor")
    constructor(
        url: String,
        quality: String,
        videoUrl: String?,
        uri: Uri? = null,
        headers: Headers? = null,
    ) : this(url, quality, videoUrl, headers) {
        @Suppress("UNUSED_VARIABLE")
        val ignored = uri
    }

    @Deprecated("Use the new Video constructor")
    fun copy(
        url: String = this.url,
        quality: String = this.quality,
        videoUrl: String? = this.videoUrl,
        headers: Headers? = this.headers,
        subtitleTracks: List<Track> = this.subtitleTracks,
        audioTracks: List<Track> = this.audioTracks,
    ): Video = Video(url, quality, videoUrl, headers, subtitleTracks, audioTracks)
}

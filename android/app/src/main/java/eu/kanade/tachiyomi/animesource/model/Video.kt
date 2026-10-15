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

/**
 * Video model of extensions-lib v17, as in Aniyomi's source-api: v17 added [memo], and the v16
 * constructor and copy stay as hidden overloads so v16 APKs still link. The v14 constructors and
 * accessors are kept for older APKs.
 */
@Suppress("unused_parameter")
data class Video(
    var videoUrl: String = "",
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
    val memo: JsonObject = JsonObject(emptyMap()),
) {
    /** Original v14 fields, still consumed by older extensions and Hibiki's adapter. */
    private var legacyUrl: String? = null

    val url: String
        get() = legacyUrl ?: videoUrl

    val quality: String
        get() = videoTitle

    /** extensions-lib 16 constructor. */
    @Deprecated("Used only for compatibility with ext lib 16", level = DeprecationLevel.HIDDEN)
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
    ) : this(
        videoUrl, videoTitle, resolution, bitrate, headers, preferred, subtitleTracks, audioTracks,
        timestamps, mpvArgs, ffmpegStreamArgs, ffmpegVideoArgs, internalData, initialized, JsonObject(emptyMap()),
    )

    /** extensions-lib 16 copy. */
    @Deprecated("Used only for compatibility with ext lib 16", level = DeprecationLevel.HIDDEN)
    fun copy(
        videoUrl: String = this.videoUrl,
        videoTitle: String = this.videoTitle,
        resolution: Int? = this.resolution,
        bitrate: Int? = this.bitrate,
        headers: Headers? = this.headers,
        preferred: Boolean = this.preferred,
        subtitleTracks: List<Track> = this.subtitleTracks,
        audioTracks: List<Track> = this.audioTracks,
        timestamps: List<TimeStamp> = this.timestamps,
        mpvArgs: List<Pair<String, String>> = this.mpvArgs,
        ffmpegStreamArgs: List<Pair<String, String>> = this.ffmpegStreamArgs,
        ffmpegVideoArgs: List<Pair<String, String>> = this.ffmpegVideoArgs,
        internalData: String = this.internalData,
        initialized: Boolean = this.initialized,
    ): Video = copy(
        videoUrl = videoUrl, videoTitle = videoTitle, resolution = resolution, bitrate = bitrate,
        headers = headers, preferred = preferred, subtitleTracks = subtitleTracks, audioTracks = audioTracks,
        timestamps = timestamps, mpvArgs = mpvArgs, ffmpegStreamArgs = ffmpegStreamArgs,
        ffmpegVideoArgs = ffmpegVideoArgs, internalData = internalData, initialized = initialized, memo = memo,
    )

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

    /** v17: the stream or one of its tracks points at the source's own [HttpServer]. */
    fun usesHttpServer(): Boolean =
        localUrl.containsMatchIn(videoUrl) ||
            audioTracks.any { localUrl.containsMatchIn(it.url) } ||
            subtitleTracks.any { localUrl.containsMatchIn(it.url) }

    /** v17: the same video with [HttpServer.PLACEHOLDER_URL] pointed at the server's real port. */
    fun copyHttpServer(port: Int): Video {
        val host = "http://localhost:$port"
        return copy(
            videoUrl = localUrl.replace(videoUrl, host),
            subtitleTracks = subtitleTracks.map { it.copy(url = localUrl.replace(it.url, host)) },
            audioTracks = audioTracks.map { it.copy(url = localUrl.replace(it.url, host)) },
            memo = memo,
        )
    }

    private companion object {
        val localUrl = Regex("""http://localhost:1(?!\d)""")
    }
}

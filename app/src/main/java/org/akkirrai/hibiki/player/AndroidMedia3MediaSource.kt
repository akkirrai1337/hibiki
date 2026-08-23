package org.akkirrai.hibiki.player

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import org.akkirrai.hibiki.core.download.OfflineMediaCache
import org.akkirrai.hibiki.player.model.PlaybackStreamType
import org.akkirrai.hibiki.player.model.PlaybackStream

/** Android Media3 mapping kept outside the shared UI/orchestration layer. */
internal fun PlaybackStream.toAndroidMediaSource(context: Context): MediaSource {
    val dataSourceFactory = OfflineMediaCache.buildPlaybackDataSourceFactory(
        context = context,
        headers = headers,
    )
    val mediaItem = MediaItem.Builder()
        .setUri(streamUrl.toUri())
        .setMimeType(
            when (streamType) {
                PlaybackStreamType.HLS -> MimeTypes.APPLICATION_M3U8
                PlaybackStreamType.MP4 -> MimeTypes.VIDEO_MP4
                PlaybackStreamType.DASH -> MimeTypes.APPLICATION_MPD
            },
        )
        .build()

    return when (streamType) {
        PlaybackStreamType.HLS -> HlsMediaSource.Factory(dataSourceFactory)
            .setAllowChunklessPreparation(true)
            .createMediaSource(mediaItem)
        PlaybackStreamType.DASH -> DashMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        PlaybackStreamType.MP4 -> ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
    }
}

package org.akkirrai.hibiki.feature.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import org.akkirrai.hibiki.R

internal object AndroidPictureInPictureActions {
    const val ToggleAudioOnly = "org.akkirrai.hibiki.action.TOGGLE_AUDIO_ONLY"
    const val TogglePlayback = "org.akkirrai.hibiki.action.TOGGLE_PLAYBACK"
    const val PreviousEpisode = "org.akkirrai.hibiki.action.PREVIOUS_EPISODE"
    const val NextEpisode = "org.akkirrai.hibiki.action.NEXT_EPISODE"
}

internal fun Context.findHibikiActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHibikiActivity()
    else -> null
}

internal fun createAndroidPictureInPictureParams(
    context: Context,
    isPlaying: Boolean,
    isAudioOnly: Boolean,
    hasPreviousEpisode: Boolean,
    hasNextEpisode: Boolean,
): PictureInPictureParams {
    val actions = buildList {
        add(
            createPictureInPictureAction(
                context = context,
                action = AndroidPictureInPictureActions.ToggleAudioOnly,
                requestCode = 1001,
                iconResId = R.drawable.ic_player_headphones_24,
                titleResId = if (isAudioOnly) R.string.watch_player_show_video else R.string.watch_player_audio_only,
            ),
        )
        add(
            createPictureInPictureAction(
                context = context,
                action = AndroidPictureInPictureActions.TogglePlayback,
                requestCode = 1002,
                iconResId = if (isPlaying) R.drawable.ic_player_media_pause_24 else R.drawable.ic_player_media_play_arrow_24,
                titleResId = if (isPlaying) R.string.watch_player_pause else R.string.watch_player_play,
            ),
        )
        if (hasPreviousEpisode) {
            add(
                createPictureInPictureAction(
                    context = context,
                    action = AndroidPictureInPictureActions.PreviousEpisode,
                    requestCode = 1003,
                    iconResId = R.drawable.ic_player_media_skip_previous_24,
                    titleResId = R.string.watch_player_previous_episode,
                ),
            )
        }
        if (hasNextEpisode) {
            add(
                createPictureInPictureAction(
                    context = context,
                    action = AndroidPictureInPictureActions.NextEpisode,
                    requestCode = 1004,
                    iconResId = R.drawable.ic_player_media_skip_next_24,
                    titleResId = R.string.watch_player_next_episode,
                ),
            )
        }
    }
    return PictureInPictureParams.Builder().setActions(actions).build()
}

private fun createPictureInPictureAction(
    context: Context,
    action: String,
    requestCode: Int,
    iconResId: Int,
    @StringRes titleResId: Int,
): RemoteAction {
    val title = context.getString(titleResId)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(action).setPackage(context.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    return RemoteAction(Icon.createWithResource(context, iconResId), title, title, pendingIntent)
}

internal fun registerPictureInPictureReceiver(
    context: Context,
    onToggleAudioOnly: () -> Unit,
    onTogglePlayback: () -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
): BroadcastReceiver {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            when (intent.action) {
                AndroidPictureInPictureActions.ToggleAudioOnly -> onToggleAudioOnly()
                AndroidPictureInPictureActions.TogglePlayback -> onTogglePlayback()
                AndroidPictureInPictureActions.PreviousEpisode -> onPreviousEpisode()
                AndroidPictureInPictureActions.NextEpisode -> onNextEpisode()
            }
        }
    }
    ContextCompat.registerReceiver(
        context,
        receiver,
        IntentFilter().apply {
            addAction(AndroidPictureInPictureActions.ToggleAudioOnly)
            addAction(AndroidPictureInPictureActions.TogglePlayback)
            addAction(AndroidPictureInPictureActions.PreviousEpisode)
            addAction(AndroidPictureInPictureActions.NextEpisode)
        },
        ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    return receiver
}

package org.akkirrai.hibiki.app.navigation

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.akkirrai.hibiki.core.discord.DiscordAuthActivity

internal class AppActivityLaunchers(
    val editAvatar: (((String) -> Unit) -> Unit),
    val signInWithDiscord: (((String) -> Unit) -> Unit),
)

@Composable
internal fun rememberAppActivityLaunchers(
    context: Context,
): AppActivityLaunchers {
    var pendingAvatarCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var pendingDiscordTokenCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.toString()?.let { pendingAvatarCallback?.invoke(it) }
        pendingAvatarCallback = null
    }
    val discordAuthLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            DiscordAuthActivity.tokenFromResult(result.data)?.let { token ->
                pendingDiscordTokenCallback?.invoke(token)
            }
        }
        pendingDiscordTokenCallback = null
    }
    return remember(avatarPicker, discordAuthLauncher, context) {
        AppActivityLaunchers(
            editAvatar = { onPicked ->
                pendingAvatarCallback = onPicked
                avatarPicker.launch(arrayOf("image/*"))
            },
            signInWithDiscord = { onToken ->
                pendingDiscordTokenCallback = onToken
                discordAuthLauncher.launch(Intent(context, DiscordAuthActivity::class.java))
            },
        )
    }
}

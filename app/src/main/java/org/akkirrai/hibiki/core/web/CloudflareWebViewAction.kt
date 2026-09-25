package org.akkirrai.hibiki.core.web

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import eu.kanade.tachiyomi.network.interceptor.CloudflareChallenges
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry

/** A button's worth of "open the page in a WebView": what to call it and what it does. */
class CloudflareWebViewAction(val label: String, val open: () -> Unit)

/**
 * An action for an error screen when the current source's site is asking for a Cloudflare check that the app
 * could not do by itself; null when there is nothing of the sort. It opens the failing page in a visible
 * WebView and, once the check is passed there, calls [onSolved] (typically the screen's retry).
 */
@Composable
fun rememberCloudflareWebViewAction(onSolved: () -> Unit): CloudflareWebViewAction? {
    val context = LocalContext.current
    val pending by CloudflareChallenges.pending.collectAsState()
    val sourceId = LocalAppPreferencesState.current.animeSource
    val latestOnSolved by rememberUpdatedState(onSolved)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            CloudflareChallenges.clear()
            latestOnSolved()
        }
    }
    val label = stringResource(R.string.action_open_web_view)
    val url = pending ?: return null
    // Only the current source's own site: a challenge left by another one is not what this screen failed on.
    val target = AnimeSourceRegistry.webViewTarget(sourceId) ?: return null
    if (CloudflareChallenges.hostOf(target.baseUrl) != CloudflareChallenges.hostOf(url)) return null
    return remember(url, sourceId, label) {
        CloudflareWebViewAction(label) {
            SourceWebView.intent(context, sourceId, url)?.let(launcher::launch)
        }
    }
}

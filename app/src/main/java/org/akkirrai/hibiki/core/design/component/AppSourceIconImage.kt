package org.akkirrai.hibiki.core.design.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import coil.compose.AsyncImage
import coil.request.ErrorResult
import org.akkirrai.hibiki.core.log.AppLogger

/**
 * Shows the icon a source extension publishes via [url]. Sources are installed as separate
 * Android apps with their own launcher icon, so this only ever renders the source's own
 * branding, not anything installed on the device -- a generic placeholder covers sources that
 * haven't provided an icon url (or haven't been resolved from a real repository index yet).
 */
@Composable
fun AppSourceIconImage(
    url: String?,
    modifier: Modifier = Modifier,
    debugTag: String = "unknown",
) {
    if (url.isNullOrBlank()) {
        Icon(
            imageVector = Icons.Outlined.Extension,
            contentDescription = null,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    AsyncImage(
        model = url,
        contentDescription = null,
        modifier = modifier,
        onError = { state ->
            AppLogger.w(
                "SourceIconImage",
                "[$debugTag] Failed to load icon $url: ${(state.result as? ErrorResult)?.throwable?.message}",
                (state.result as? ErrorResult)?.throwable,
            )
        },
    )
}

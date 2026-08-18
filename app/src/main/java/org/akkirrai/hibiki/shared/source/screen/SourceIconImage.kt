package org.akkirrai.hibiki.shared.source

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import coil3.compose.AsyncImage

/**
 * Shows the icon the source itself publishes via [url]. Sources are installed as separate
 * Android apps with their own system launcher icon, so this only ever renders the source's own
 * branding, not anything installed on the device - a generic placeholder covers sources that
 * haven't provided an icon url yet.
 */
@Composable
fun AppSourceIconImage(
    url: String?,
    placeholder: Painter? = null,
    modifier: Modifier = Modifier,
) {
    val effectivePlaceholder = placeholder ?: rememberVectorPainter(Icons.Outlined.Extension)
    AsyncImage(
        model = url,
        placeholder = effectivePlaceholder,
        error = effectivePlaceholder,
        contentDescription = null,
        modifier = modifier,
    )
}

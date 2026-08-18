package org.akkirrai.hibiki.shared.source

import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil3.compose.AsyncImage

/**
 * Prefers the real icon bundled in the installed extension APK (read via PackageManager, same
 * as a launcher icon) over [url] or the generic placeholder, which covers every source that
 * isn't installed yet and has no icon URL to load from - no per-source icon setup needed.
 */
@Composable
fun AppSourceIconImage(
    url: String?,
    placeholder: Painter? = null,
    installedPackageName: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val installedIconPainter = remember(installedPackageName) {
        installedPackageName?.let { packageName ->
            runCatching {
                context.packageManager.getApplicationIcon(packageName)
            }.getOrNull()
        }
    }?.let { drawable ->
        remember(drawable) { BitmapPainter(drawable.toBitmap().asImageBitmap()) }
    }

    if (installedIconPainter != null) {
        Image(painter = installedIconPainter, contentDescription = null, modifier = modifier)
        return
    }

    val effectivePlaceholder = placeholder ?: rememberVectorPainter(Icons.Outlined.Extension)
    AsyncImage(
        model = url,
        placeholder = effectivePlaceholder,
        error = effectivePlaceholder,
        contentDescription = null,
        modifier = modifier,
    )
}

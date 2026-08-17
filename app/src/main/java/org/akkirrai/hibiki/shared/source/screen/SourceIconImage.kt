package org.akkirrai.hibiki.shared.source

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.graphics.drawable.toBitmap
import coil3.compose.AsyncImage
import org.akkirrai.hibiki.R

/**
 * Prefers the real icon bundled in the installed extension APK (read via PackageManager, same
 * as a launcher icon) over [url] or the hardcoded per-source drawables, which only exist as a
 * fallback for extensions that aren't installed yet and have no icon URL to load from.
 */
@Composable
fun AppSourceIconImage(
    url: String?,
    placeholder: Painter? = null,
    sourceId: String? = null,
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

    val effectivePlaceholder = placeholder ?: when (sourceId) {
        "yummy-anime" -> painterResource(R.drawable.source_yummy_anime)
        "ani-liberty" -> painterResource(R.drawable.source_ani_liberty)
        "animego" -> painterResource(R.drawable.source_animego)
        "animepahe" -> painterResource(R.drawable.source_animepahe)
        else -> null
    }
    AsyncImage(
        model = url,
        placeholder = effectivePlaceholder,
        error = effectivePlaceholder,
        contentDescription = null,
        modifier = modifier,
    )
}

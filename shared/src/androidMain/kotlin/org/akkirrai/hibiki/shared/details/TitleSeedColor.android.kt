package org.akkirrai.hibiki.shared.details

import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil3.Image
import coil3.toBitmap

actual suspend fun extractTitleSeedColor(image: Image): Long? {
    val bitmap = runCatching { image.toBitmap(width = 96, height = 96) }.getOrNull() ?: return null
    val swatch = runCatching {
        Palette.from(bitmap)
            .maximumColorCount(24)
            .generate()
    }.getOrNull() ?: return null
    return (
        swatch.vibrantSwatch
            ?: swatch.lightVibrantSwatch
            ?: swatch.darkVibrantSwatch
            ?: swatch.mutedSwatch
            ?: swatch.dominantSwatch
        )?.rgb?.let { Color(it).value.toLong() }
}

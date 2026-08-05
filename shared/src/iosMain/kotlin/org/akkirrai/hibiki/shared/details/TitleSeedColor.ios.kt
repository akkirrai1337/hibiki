package org.akkirrai.hibiki.shared.details.model

import androidx.compose.ui.graphics.Color
import coil3.Image
import coil3.toBitmap
import kotlin.math.max
import kotlin.math.min

actual suspend fun extractTitleSeedColor(image: Image): Long? {
    val bitmap = runCatching { image.toBitmap(width = 96, height = 96) }.getOrNull() ?: return null
    var bestColor: Int? = null
    var bestScore = Float.NEGATIVE_INFINITY
    val stepX = max(1, bitmap.width / 12)
    val stepY = max(1, bitmap.height / 12)
    for (y in 0 until bitmap.height step stepY) {
        for (x in 0 until bitmap.width step stepX) {
            val argb = bitmap.getColor(x, y)
            val red = (argb shr 16 and 0xff) / 255f
            val green = (argb shr 8 and 0xff) / 255f
            val blue = (argb and 0xff) / 255f
            val maximum = max(red, max(green, blue))
            val minimum = min(red, min(green, blue))
            val saturation = if (maximum == 0f) 0f else (maximum - minimum) / maximum
            val score = saturation * 0.7f + maximum * 0.3f
            if (score > bestScore) {
                bestScore = score
                bestColor = argb
            }
        }
    }
    return bestColor?.let { Color(it).value.toLong() }
}

package org.akkirrai.hibiki.profile

import kotlin.math.round
import org.akkirrai.hibiki.library.LibraryCategory

fun formatDurationHours(durationMs: Long): String {
    if (durationMs <= 0L) return "0"
    val tenths = round(durationMs / 3_600_000.0 * 10.0).toLong()
    return "${tenths / 10L}.${tenths % 10L}"
}

fun formatProfileRating(value: Double): String {
    if (value % 1.0 == 0.0) return value.toInt().toString()
    val roundedHundredths = round(value * 100.0).toLong()
    val whole = roundedHundredths / 100L
    val fraction = (roundedHundredths % 100L).let { if (it < 0) -it else it }
    return "$whole.${fraction.toString().padStart(2, '0')}"
}

fun normalizePosterUrl(rawUrl: String?): String? = rawUrl?.trim()?.takeIf {
    it.startsWith("http://", true) || it.startsWith("https://", true)
}

fun Set<LibraryCategory>.primaryLibraryCategory(): LibraryCategory =
    LibraryCategory.entries.firstOrNull {
        it != LibraryCategory.Saved && it != LibraryCategory.Recent && it in this
    } ?: LibraryCategory.Saved

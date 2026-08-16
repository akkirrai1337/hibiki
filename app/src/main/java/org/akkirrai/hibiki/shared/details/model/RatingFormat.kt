package org.akkirrai.hibiki.shared.details.model

import kotlin.math.round

fun formatRating(value: Double): String {
    val roundedHundredths = round(value * 100.0).toLong()
    val whole = roundedHundredths / 100L
    val fraction = (roundedHundredths % 100L).let { if (it < 0) -it else it }
    return "$whole.${fraction.toString().padStart(2, '0')}"
}

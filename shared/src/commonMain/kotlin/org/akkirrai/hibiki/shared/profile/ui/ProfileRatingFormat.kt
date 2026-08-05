package org.akkirrai.hibiki.shared.profile

import kotlin.math.round

fun formatProfileRating(value: Double): String {
    if (value % 1.0 == 0.0) return value.toInt().toString()
    val roundedHundredths = round(value * 100.0).toLong()
    val whole = roundedHundredths / 100L
    val fraction = (roundedHundredths % 100L).let { if (it < 0) -it else it }
    return "$whole.${fraction.toString().padStart(2, '0')}"
}

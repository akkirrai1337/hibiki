package org.akkirrai.hibiki.shared.player

fun formatEpisodeNumber(number: Double): String =
    if (number % 1.0 == 0.0) number.toInt().toString() else number.toString()

package org.akkirrai.hibiki.shared.home.ui

fun resolveDisplayTypeLabel(rawType: String): String = when (rawType.uppercase()) {
    "TV" -> "TV"
    "TV_SHORT" -> "TV Short"
    "OVA" -> "OVA"
    "ONA" -> "ONA"
    "MOVIE" -> "Movie"
    "SHORT_MOVIE", "SHORT-MOVIE" -> "Short Movie"
    "SPECIAL" -> "Special"
    else -> rawType.replace("_", "-").replace("-", " ")
        .replaceFirstChar { it.uppercase() }
}

package org.akkirrai.hibiki.shared.home

fun formatEpisodesCountLabel(count: Int, preferEnglish: Boolean): String =
    if (preferEnglish) "$count episodes" else "$count \u0441\u0435\u0440\u0438\u0439"

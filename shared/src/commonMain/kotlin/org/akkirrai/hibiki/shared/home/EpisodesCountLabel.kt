package org.akkirrai.hibiki.shared.home

fun formatEpisodesCountLabel(count: Int, preferEnglish: Boolean): String =
    if (preferEnglish) "$count episodes" else "$count серий"

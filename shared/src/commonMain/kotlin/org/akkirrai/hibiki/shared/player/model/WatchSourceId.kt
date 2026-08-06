package org.akkirrai.hibiki.shared.player

const val WATCH_SOURCE_SEPARATOR = "|watch|"

fun watchTitleIdFromSourceId(sourceId: String): String =
    if (WATCH_SOURCE_SEPARATOR in sourceId) {
        sourceId.substringBefore(WATCH_SOURCE_SEPARATOR)
    } else if ('|' in sourceId) {
        sourceId.substringBefore('|')
    } else {
        sourceId.substringBefore(':')
    }

fun buildWatchSourceId(animeId: String, dubbingTitle: String, index: Int): String {
    val slug = dubbingTitle.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), "-")
        .trim('-')
        .ifBlank { "voiceover-$index" }
    return "$animeId$WATCH_SOURCE_SEPARATOR$slug-$index"
}

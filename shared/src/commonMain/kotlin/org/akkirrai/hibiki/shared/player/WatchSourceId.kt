package org.akkirrai.hibiki.shared.player

const val WATCH_SOURCE_SEPARATOR = "|watch|"

fun watchTitleIdFromSourceId(sourceId: String): String =
    if (WATCH_SOURCE_SEPARATOR in sourceId) {
        sourceId.substringBefore(WATCH_SOURCE_SEPARATOR)
    } else {
        sourceId.substringBefore(':')
    }

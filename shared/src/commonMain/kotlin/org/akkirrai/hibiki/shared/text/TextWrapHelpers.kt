package org.akkirrai.hibiki.shared.text

fun String.preventTrailingOrphanWrap(): String {
    val trimmed = trim()
    val lastSpaceIndex = trimmed.indexOfLast { it.isWhitespace() }
    if (lastSpaceIndex <= 0 || lastSpaceIndex >= trimmed.lastIndex) return this
    return buildString(trimmed.length) {
        append(trimmed, 0, lastSpaceIndex)
        append('\u00A0')
        append(trimmed, lastSpaceIndex + 1, trimmed.length)
    }
}

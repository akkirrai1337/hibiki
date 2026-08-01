package org.akkirrai.hibiki.shared.text

fun String.preventTrailingOrphanWrap(): String {
    val trimmed = trim()
    val words = trimmed.split(Regex("\\s+"))
    if (words.size <= 1) return this

    return buildString(trimmed.length) {
        words.forEachIndexed { index, word ->
            if (index > 0) {
                val isSingleCharacterWord = word.length == 1
                val isTrailingWord = index == words.lastIndex
                append(if (isSingleCharacterWord || isTrailingWord) '\u00A0' else ' ')
            }
            append(word)
        }
    }
}

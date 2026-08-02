package org.akkirrai.hibiki.shared.text

fun String.preventTrailingOrphanWrap(): String {
    val trimmed = trim()
    val words = trimmed.split(Regex("\\s+"))
    if (words.size <= 1) return this

    return buildString(trimmed.length) {
        words.forEachIndexed { index, word ->
            if (index > 0) {
                val previousWord = words[index - 1]
                val singleCharacterWordNeedsNextWord = previousWord.length == 1
                val trailingSingleCharacterWord = word.length == 1 && index == words.lastIndex
                val isTrailingWord = index == words.lastIndex
                append(
                    if (singleCharacterWordNeedsNextWord || trailingSingleCharacterWord || isTrailingWord) {
                        '\u00A0'
                    } else {
                        ' '
                    },
                )
            }
            append(word)
        }
    }
}

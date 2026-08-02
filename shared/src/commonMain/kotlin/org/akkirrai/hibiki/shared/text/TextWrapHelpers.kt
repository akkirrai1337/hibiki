package org.akkirrai.hibiki.shared.text

private const val WordJoiner = '\u2060'

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

fun String.preventWordBreaks(): String = buildString(length) {
    var insideWord = false
    var previousWasHighSurrogate = false

    for (character in this@preventWordBreaks) {
        val isNonBreakingSpace = character == '\u00A0'
        val isWordCharacter = !character.isWhitespace() && !isNonBreakingSpace
        if (isWordCharacter) {
            if (insideWord && !previousWasHighSurrogate) append(WordJoiner)
            insideWord = true
        } else {
            insideWord = false
        }
        append(character)
        previousWasHighSurrogate = character.isHighSurrogate()
    }
}

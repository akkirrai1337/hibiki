package org.akkirrai.hibiki.core.source.extension

/**
 * Some extensions append machine-made lines to a plain-text description, in Markdown that nothing
 * here renders:
 *
 *     Second season of ...
 *
 *     **Rating:** PG 13
 *     **Subtitles:** English, Deutsch, ...
 *     [MAL](https://myanimelist.net/anime/61897)
 *
 * Those lines are data, not prose. The age rating belongs in the age-rating field; the rest is
 * dropped so the description reads as a description. Only a trailing block made entirely of such
 * lines is touched, so bold text inside the story itself is left alone.
 */
internal object SourceDescriptionFooter {
    data class Parsed(val text: String?, val ageRating: String?)

    private val LABELLED = Regex("""^\*\*([^*:]+):\*\*\s*(.*)$""")
    private val LINK_ONLY = Regex("""^\[[^\]]+]\(https?://[^)\s]+\)$""")

    fun parse(description: String?): Parsed {
        if (description.isNullOrBlank()) return Parsed(description, null)
        val lines = description.lines().toMutableList()
        var ageRating: String? = null
        var removedAny = false
        while (lines.isNotEmpty()) {
            val line = lines.last().trim()
            if (line.isEmpty()) {
                lines.removeLast()
                continue
            }
            val labelled = LABELLED.matchEntire(line)
            when {
                labelled != null -> {
                    if (labelled.groupValues[1].trim().equals("Rating", ignoreCase = true)) {
                        ageRating = labelled.groupValues[2].trim().takeIf(String::isNotBlank)
                    }
                }
                LINK_ONLY.matches(line) -> Unit
                else -> break
            }
            lines.removeLast()
            removedAny = true
        }
        if (!removedAny) return Parsed(description, null)
        val text = lines.joinToString("\n").trim().takeIf(String::isNotBlank)
        return Parsed(text, ageRating)
    }
}

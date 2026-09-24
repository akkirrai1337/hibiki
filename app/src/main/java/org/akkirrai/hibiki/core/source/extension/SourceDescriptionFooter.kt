package org.akkirrai.hibiki.core.source.extension

/**
 * Some extensions append machine-made facts to a plain-text description, in one of two shapes:
 *
 *     Second season of ...
 *
 *     **Rating:** PG 13                                   <- a line per fact, in Markdown
 *     **Subtitles:** English, Deutsch
 *     [MAL](https://myanimelist.net/anime/61897)
 *
 *     Other name: BLEACH | Type: TV | Aired: Oct 5, 2004 | Rating: PG-13 | Episodes: 366
 *                                                         <- one line of "Key: value | Key: value"
 *
 * Those lines are data, not prose. They are taken out of the description and their facts returned so
 * the app can show them in its own fields. Only a trailing block made entirely of such lines is
 * touched, so bold text or a colon inside the story itself is left alone.
 */
internal object SourceDescriptionFooter {
    /** [facts] are keyed by the lower-cased label the extension used ("rating", "episodes", ...). */
    data class Parsed(val text: String?, val facts: Map<String, String>) {
        val ageRating: String? get() = facts["rating"]
    }

    private val LABELLED = Regex("""^\*\*([^*:]+):\*\*\s*(.*)$""")
    private val LINK_ONLY = Regex("""^\[[^\]]+]\(https?://[^)\s]+\)$""")
    private val PIPE_FACT = Regex("""^([A-Za-z][A-Za-z ]{0,24}):\s*(.*)$""")

    fun parse(description: String?): Parsed {
        if (description.isNullOrBlank()) return Parsed(description, emptyMap())
        val lines = description.lines().toMutableList()
        val facts = linkedMapOf<String, String>()
        var removedAny = false
        while (lines.isNotEmpty()) {
            val line = lines.last().trim()
            if (line.isEmpty()) {
                lines.removeLast()
                continue
            }
            val labelled = LABELLED.matchEntire(line)
            val pipeFacts = if (labelled == null) pipeFacts(line) else null
            when {
                labelled != null -> putFact(facts, labelled.groupValues[1], labelled.groupValues[2])
                pipeFacts != null -> pipeFacts.forEach { (key, value) -> putFact(facts, key, value) }
                LINK_ONLY.matches(line) -> Unit
                else -> break
            }
            lines.removeLast()
            removedAny = true
        }
        if (!removedAny) return Parsed(description, emptyMap())
        val text = lines.joinToString("\n").trim().takeIf(String::isNotBlank)
        return Parsed(text, facts)
    }

    /** The facts of a "Key: value | Key: value" line, or null when the line is not one. */
    private fun pipeFacts(line: String): List<Pair<String, String>>? {
        val segments = line.split('|').map(String::trim).filter(String::isNotEmpty)
        if (segments.size < 2) return null
        return segments.map { segment ->
            val match = PIPE_FACT.matchEntire(segment) ?: return null
            match.groupValues[1] to match.groupValues[2]
        }
    }

    private fun putFact(facts: MutableMap<String, String>, key: String, value: String) {
        val cleaned = value.trim().trim('-', '–', '—', ' ').takeIf(String::isNotBlank) ?: return
        // A block is read from its end, so the first value seen for a label is the last one written.
        facts.putIfAbsent(key.trim().lowercase(), cleaned)
    }
}

/** What a source's footer facts say about a title, in the app's own terms. */
internal data class FooterFacts(
    val ageRating: String?,
    val episodeCount: Int?,
    val type: String?,
    val year: Int?,
    val season: Int?,
    val synonyms: List<String>,
) {
    companion object {
        private val PREMIERED = Regex("""(?i)\b(winter|spring|summer|fall|autumn)\s+(\d{4})\b""")
        private val YEAR = Regex("""\b(19|20)\d{2}\b""")

        fun from(facts: Map<String, String>): FooterFacts {
            val premiered = facts["premiered"]?.let(PREMIERED::find)
            val season = premiered?.groupValues?.get(1)?.lowercase()?.let {
                when (it) {
                    "winter" -> 1
                    "spring" -> 2
                    "summer" -> 3
                    else -> 4
                }
            }
            val year = premiered?.groupValues?.get(2)?.toIntOrNull()
                ?: (facts["aired"] ?: facts["year"])?.let(YEAR::find)?.value?.toIntOrNull()
            return FooterFacts(
                ageRating = facts["rating"],
                episodeCount = facts["episodes"]?.takeIf { it.all(Char::isDigit) }?.toIntOrNull()?.takeIf { it > 0 },
                type = facts["type"],
                year = year,
                season = season,
                synonyms = listOfNotNull(facts["other name"], facts["synonyms"])
                    .flatMap { it.split(',', ';') }
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .distinct(),
            )
        }
    }
}

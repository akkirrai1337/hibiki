package org.akkirrai.hibiki.core.source.extension

/**
 * Some extensions put machine-made facts into a plain-text description: a line of rating stars at
 * the top ("★★★★☆ 7.21"), and/or facts appended at the end, in one of two shapes:
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
        /** The age rating ("PG-13"); a numeric "Rating" is a score and is read by [FooterFacts]. */
    val ageRating: String? get() = facts["rating"]?.takeUnless(FooterFacts::isScore)
    }

    private val LABELLED = Regex("""^\*\*([^*:]+):\*\*\s*(.*)$""")
    private val LINK_ONLY = Regex("""^\[[^\]]+]\(https?://[^)\s]+\)$""")
    private val PIPE_FACT = Regex("""^([A-Za-z][A-Za-z ]{0,24}):\s*(.*)$""")

    /** A first line made of rating stars, optionally with the number they stand for: "★★★★☆ 7.21". */
    private val STAR_HEADER = Regex("""^([★☆✩✮✭⭐]{2,10})\s*(\d{1,3}(?:[.,]\d+)?)?\s*$""")

    /** A lone "Key: value" line only counts when its key is one of these - prose also has colons. */
    private val PLAIN_KEYS = setOf(
        "score", "mal score", "mean score", "rating", "studio", "studios", "genre", "genres", "status", "type",
        "aired", "premiered", "episodes", "duration", "year", "released", "source", "producers", "licensors",
        "synonyms", "other name", "japanese", "english", "native",
    )

    fun parse(description: String?): Parsed {
        if (description.isNullOrBlank()) return Parsed(description, emptyMap())
        val lines = description.lines().toMutableList()
        val facts = linkedMapOf<String, String>()
        var removedAny = false
        // Some extensions open the description with the rating as stars; it is the score, not prose.
        val headerIndex = lines.indexOfFirst { it.isNotBlank() }
        val header = lines.getOrNull(headerIndex)?.trim()?.let(STAR_HEADER::matchEntire)
        if (header != null) {
            val printed = header.groupValues[2].replace(',', '.').takeIf(String::isNotBlank)
            // Without a number the stars themselves are the score, on a five-star scale.
            val score = printed ?: header.groupValues[1].count { it == '★' || it == '✭' || it == '⭐' }
                .takeIf { it > 0 }?.let { (it * 2).toString() }
            score?.let { facts["score"] = it }
            lines.subList(0, headerIndex + 1).clear()
            removedAny = true
        }
        while (lines.isNotEmpty()) {
            val line = lines.last().trim()
            if (line.isEmpty()) {
                lines.removeLast()
                continue
            }
            val labelled = LABELLED.matchEntire(line)
            val pipeFacts = if (labelled == null) pipeFacts(line) else null
            val plain = if (labelled == null && pipeFacts == null) plainFact(line) else null
            when {
                labelled != null -> putFact(facts, labelled.groupValues[1], labelled.groupValues[2])
                pipeFacts != null -> pipeFacts.forEach { (key, value) -> putFact(facts, key, value) }
                plain != null -> putFact(facts, plain.first, plain.second)
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

    private fun plainFact(line: String): Pair<String, String>? {
        val match = PIPE_FACT.matchEntire(line) ?: return null
        return match.takeIf { it.groupValues[1].trim().lowercase() in PLAIN_KEYS }
            ?.let { it.groupValues[1] to it.groupValues[2] }
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
    /** A 0-10 score the source printed in the description, if any. */
    val score: Double?,
    val studios: List<String>,
    val episodeCount: Int?,
    val type: String?,
    val year: Int?,
    val season: Int?,
    val synonyms: List<String>,
) {
    companion object {
        private val PREMIERED = Regex("""(?i)\b(winter|spring|summer|fall|autumn)\s+(\d{4})\b""")
        private val YEAR = Regex("""\b(19|20)\d{2}\b""")
        private val SCORE = Regex("""^(\d{1,3}(?:[.,]\d+)?)\s*(?:/\s*(10|100))?$""")

        /** Whether a "Rating" value is a number (a score) rather than an age rating like "PG-13". */
        fun isScore(value: String): Boolean = SCORE.matches(value.trim())

        private fun parseScore(value: String?): Double? {
            val match = value?.trim()?.let(SCORE::matchEntire) ?: return null
            val number = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
            val scale = match.groupValues[2].toIntOrNull()
            val outOfTen = when {
                scale == 100 -> number / 10.0
                scale == 10 || number <= 10.0 -> number
                number <= 100.0 -> number / 10.0
                else -> return null
            }
            return outOfTen.takeIf { it > 0.0 }
        }

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
                ageRating = facts["rating"]?.takeUnless(::isScore),
                score = parseScore(facts["score"] ?: facts["mal score"] ?: facts["mean score"] ?: facts["rating"]),
                studios = listOfNotNull(facts["studios"], facts["studio"])
                    .flatMap { it.split(',', ';') }
                    .map(String::trim)
                    .filter(String::isNotBlank),
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

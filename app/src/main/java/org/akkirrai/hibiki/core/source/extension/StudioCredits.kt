package org.akkirrai.hibiki.core.source.extension

/**
 * Aniyomi has no studio field, so extensions put studios into `author` - and some put more in the
 * same string, such as `Studio Pierrot (**Producers:** Dentsu, Shueisha, Aniplex)`. The names before
 * the first Markdown label are studios; a `Producers` label starts the producer list; any other
 * label (licensors, ...) is dropped.
 */
internal object StudioCredits {
    data class Credits(val studios: List<String>, val producers: List<String>)

    private val PRODUCERS = Regex("""\*\*\s*Producers?\s*:\s*\*\*([^*]*)""", RegexOption.IGNORE_CASE)

    fun parse(raw: String?): Credits {
        if (raw.isNullOrBlank()) return Credits(emptyList(), emptyList())
        val marker = raw.indexOf("**")
        val head = if (marker >= 0) raw.substring(0, marker) else raw
        val producers = PRODUCERS.find(raw)?.groupValues?.get(1)
        return Credits(studios = names(head), producers = names(producers.orEmpty()))
    }

    /** Comma or semicolon separated names, without the brackets a label leaves behind, and without repeats. */
    private fun names(text: String): List<String> =
        text.split(',', ';')
            .map { it.trim().trim('(', ')', ' ') }
            .filter(String::isNotBlank)
            .distinct()
}

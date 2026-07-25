package org.akkirrai.hibiki.shared.player

private val episodeTitlePattern = Regex("""^Episode\s+(.+)$""", RegexOption.IGNORE_CASE)

fun fallbackEpisodeNumberFromTitle(title: String): String? = episodeTitlePattern
    .matchEntire(title.trim())
    ?.groupValues
    ?.getOrNull(1)
    ?.takeIf(String::isNotBlank)

package org.akkirrai.hibiki.shared.details.model

fun extractNextEpisodeNumber(episodesLabel: String): Int? {
    val releasedEpisodes = Regex("""\d+""").find(episodesLabel)?.value?.toIntOrNull() ?: return null
    return releasedEpisodes.takeIf { it >= 0 }?.plus(1)
}

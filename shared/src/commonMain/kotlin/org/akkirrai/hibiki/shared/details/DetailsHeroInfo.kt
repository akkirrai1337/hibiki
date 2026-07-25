package org.akkirrai.hibiki.shared.details

import org.akkirrai.hibiki.shared.model.Anime

data class DetailsHeroInfo(
    val type: String,
    val releaseDate: String,
    val episodes: String,
    val status: String,
    val studio: String,
)

fun resolveDetailsHeroInfo(
    anime: Anime,
    localizedEpisodeWord: String,
): DetailsHeroInfo {
    val parts = anime.subtitle
        .split(Regex("\\s*[·|]\\s*"))
        .map(String::trim)
        .filter(String::isNotEmpty)

    val type = parts.getOrNull(0)?.uppercase().orEmpty().ifBlank { "TV" }
    val year = parts.getOrNull(1).orEmpty()
    val rawEpisodes = anime.episodesLabel
        .replace(Regex("\\bepisodes?\\b", RegexOption.IGNORE_CASE), localizedEpisodeWord)
        .takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
        .orEmpty()
    val episodeCount = Regex("\\d+").find(rawEpisodes)?.value?.toIntOrNull()

    return DetailsHeroInfo(
        type = type,
        releaseDate = anime.releaseDate?.takeIf(::isKnownValue)
            ?: year.takeIf(::isKnownValue).orEmpty(),
        episodes = rawEpisodes.takeIf { episodeCount == null || episodeCount > 0 }.orEmpty(),
        status = anime.status.takeUnless { it.isBlank() || it.equals("Unknown", ignoreCase = true) }.orEmpty(),
        studio = anime.studios.joinToString(", "),
    )
}

private fun isKnownValue(value: String): Boolean = value.trim().let {
    it.isNotEmpty() && !it.equals("Unknown", ignoreCase = true) && it != "0"
}

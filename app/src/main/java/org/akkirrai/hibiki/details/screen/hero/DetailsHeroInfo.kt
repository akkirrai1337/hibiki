package org.akkirrai.hibiki.details.screen

import org.akkirrai.hibiki.details.model.*

import org.akkirrai.hibiki.home.ui.resolveDisplayTypeLabel
import org.akkirrai.hibiki.catalog.model.Anime

data class DetailsHeroInfo(
    val type: String,
    val releaseDate: String,
    val episodes: String,
    val nextEpisodeNumber: Int?,
    val status: String,
    val studio: String,
)

fun resolveDetailsHeroInfo(
    anime: Anime,
): DetailsHeroInfo {
    val parts = anime.subtitle
        .split(Regex("\\s*[\\u00B7|]\\s*"))
        .map(String::trim)
        .filter(String::isNotEmpty)

    val type = parts.getOrNull(0)
        ?.let(::resolveDisplayTypeLabel)
        ?.uppercase()
        .orEmpty()
        .ifBlank { "TV" }
    val year = parts.getOrNull(1).orEmpty()
    // episodesLabel already comes fully localized/pluralized from resolveEpisodesLabel -- an
    // English "episode(s)" -> localizedEpisodeWord swap used to live here to patch around that
    // not being true yet, but it undid correct singular/plural wording and always capitalized
    // the result, so it's gone now that the label is right at the source.
    val rawEpisodes = anime.episodesLabel
        .takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
        .orEmpty()
    val episodeCount = Regex("\\d+").find(rawEpisodes)?.value?.toIntOrNull()

    return DetailsHeroInfo(
        type = type,
        releaseDate = anime.releaseDate?.takeIf(::isKnownValue)
            ?: year.takeIf(::isKnownValue).orEmpty(),
        episodes = rawEpisodes.takeIf { episodeCount == null || episodeCount > 0 }.orEmpty(),
        nextEpisodeNumber = extractNextEpisodeNumber(anime.episodesLabel),
        status = anime.status.takeUnless { it.isBlank() || it.equals("Unknown", ignoreCase = true) }.orEmpty(),
        studio = anime.studios.joinToString(", "),
    )
}

private fun isKnownValue(value: String): Boolean = value.trim().let {
    it.isNotEmpty() && !it.equals("Unknown", ignoreCase = true) && it != "0"
}

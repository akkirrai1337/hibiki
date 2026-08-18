package org.akkirrai.hibiki.shared.details.model

import kotlin.math.round

fun extractNextEpisodeNumber(episodesLabel: String): Int? {
    val releasedEpisodes = Regex("""\d+""").find(episodesLabel)?.value?.toIntOrNull() ?: return null
    return releasedEpisodes.takeIf { it >= 0 }?.plus(1)
}

fun formatRating(value: Double): String {
    val roundedHundredths = round(value * 100.0).toLong()
    val whole = roundedHundredths / 100L
    val fraction = (roundedHundredths % 100L).let { if (it < 0) -it else it }
    return "$whole.${fraction.toString().padStart(2, '0')}"
}

fun formatCompactCount(value: Long): String = when {
    value >= 1_000_000L -> formatCompactUnit(value, 1_000_000L, "M")
    value >= 1_000L -> formatCompactUnit(value, 1_000L, "K")
    else -> value.toString()
}

private fun formatCompactUnit(value: Long, unit: Long, suffix: String): String {
    val tenths = (value * 10L + unit / 2L) / unit
    return "${tenths / 10L}.${tenths % 10L}$suffix"
}

fun isAnnouncementStatus(status: String, episodesLabel: String = ""): Boolean =
    listOf(status, episodesLabel).map { it.trim().lowercase() }.any {
        it == "анонс" || it == "announcement" || it == "announced" || it == "anons"
    }

fun formatRelatedAnimeMetadata(
    year: Int?,
    type: String?,
    status: String? = null,
    announcementLabel: String = "announcement",
): String {
    val releaseLabel = year?.takeIf { it > 0 }?.toString()
        ?: announcementLabel.takeIf { isAnnouncementStatus(status.orEmpty()) }
    val typeLabel = type?.trim()?.takeIf(String::isNotBlank)
        ?.replace('_', ' ')
        ?.replace('-', ' ')
        ?.uppercase()
    return listOfNotNull(releaseLabel, typeLabel).joinToString(" • ")
}

data class SourceMaterialLabels(
    val manga: String,
    val manhwa: String,
    val manhua: String,
    val lightNovel: String,
    val webNovel: String,
    val visualNovel: String,
    val game: String,
    val original: String,
)

fun resolveSourceMaterialLabel(
    sourceMaterial: String?,
    labels: SourceMaterialLabels,
): String? {
    val normalized = sourceMaterial?.trim()?.lowercase() ?: return null
    return when (normalized) {
        "манга", "manga" -> labels.manga
        "манхва", "manhwa" -> labels.manhwa
        "маньхуа", "manhua" -> labels.manhua
        "ранобэ", "light novel" -> labels.lightNovel
        "веб-новелла", "web novel" -> labels.webNovel
        "визуальная новелла", "visual novel" -> labels.visualNovel
        "игра", "game" -> labels.game
        "оригинал", "original" -> labels.original
        else -> sourceMaterial
    }
}

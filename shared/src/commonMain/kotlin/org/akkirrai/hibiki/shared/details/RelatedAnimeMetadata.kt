package org.akkirrai.hibiki.shared.details

fun isAnnouncementStatus(status: String, episodesLabel: String = ""): Boolean =
    listOf(status, episodesLabel).map { it.trim().lowercase() }.any {
        it == "анонс" || it == "announcement" || it == "announced" || it == "anons"
    }

fun isOngoingStatus(status: String): Boolean {
    val normalized = status.trim().lowercase()
    return normalized.contains("ongoing") ||
        normalized.contains("airing") ||
        normalized.contains("releasing") ||
        normalized.contains("онгоинг") ||
        normalized.contains("онґоінг") ||
        normalized.contains("выходит") ||
        normalized.contains("триває")
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

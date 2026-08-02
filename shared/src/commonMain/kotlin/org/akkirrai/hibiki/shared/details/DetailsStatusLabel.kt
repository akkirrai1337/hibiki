package org.akkirrai.hibiki.shared.details

fun resolveDetailsStatusLabel(
    status: String,
    ongoingLabel: String,
    releasedLabel: String,
    announcementLabel: String,
): String = when (status.trim().lowercase()) {
    "ongoing", "airing", "releasing" -> ongoingLabel
    "released", "completed", "finished", "finished airing" -> releasedLabel
    "announcement", "announced", "anons", "not yet aired" -> announcementLabel
    else -> status
}

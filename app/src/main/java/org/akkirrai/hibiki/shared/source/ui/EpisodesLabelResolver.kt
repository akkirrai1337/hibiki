package org.akkirrai.hibiki.shared.source

fun resolveEpisodesLabel(
    releasedCount: Int?,
    fallbackLabel: String?,
    preferEnglish: Boolean,
): String {
    val normalizedFallback = fallbackLabel?.trim()?.lowercase().orEmpty()
    val fallbackIsUnknown = normalizedFallback == "unknown" || normalizedFallback.contains("unknown") ||
        normalizedFallback == "episode unknown" ||
        normalizedFallback == "episodes unknown" ||
        normalizedFallback == "количество серий неизвестно"
    return when (val count = releasedCount) {
        null -> fallbackLabel.orEmpty().takeUnless { fallbackIsUnknown || it.isBlank() } ?:
            if (preferEnglish) "Episodes unknown" else "Количество серий неизвестно"
        else -> if (preferEnglish) "$count episodes" else "$count серий"
    }
}

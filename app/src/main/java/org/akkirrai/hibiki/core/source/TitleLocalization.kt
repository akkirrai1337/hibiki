package org.akkirrai.hibiki.core.source

import org.akkirrai.beakokit.model.AnimeReleaseStatus

fun AnimeReleaseStatus.localizedDisplayName(preferEnglish: Boolean): String = when (this) {
    AnimeReleaseStatus.ONGOING -> if (preferEnglish) "Ongoing" else "Онгоинг"
    AnimeReleaseStatus.RELEASED -> if (preferEnglish) "Released" else "Вышел"
    AnimeReleaseStatus.ANNOUNCEMENT -> if (preferEnglish) "Announcement" else "Анонс"
    AnimeReleaseStatus.UNKNOWN -> if (preferEnglish) "Unknown" else "Неизвестно"
}

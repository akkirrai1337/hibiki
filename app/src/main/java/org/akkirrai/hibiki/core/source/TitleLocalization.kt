package org.akkirrai.hibiki.core.source

import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.AnimeReleaseStatus
import org.akkirrai.hibiki.app.settings.LanguageMode

fun AnimeTitle.localizedDisplayName(languageMode: LanguageMode, systemLanguage: String?): String {
    val preferEnglish = when (languageMode) {
        LanguageMode.ENGLISH -> true
        LanguageMode.RUSSIAN -> false
        LanguageMode.SYSTEM -> systemLanguage != "ru"
    }
    return if (preferEnglish) {
        englishName?.takeIf(String::isNotBlank)
            ?: originalName.takeIf(String::isNotBlank)
            ?: russianName.orEmpty()
    } else {
        russianName?.takeIf(String::isNotBlank)
            ?: englishName?.takeIf(String::isNotBlank)
            ?: originalName
    }
}

fun AnimeReleaseStatus.localizedDisplayName(preferEnglish: Boolean): String = when (this) {
    AnimeReleaseStatus.ONGOING -> if (preferEnglish) "Ongoing" else "Онгоинг"
    AnimeReleaseStatus.RELEASED -> if (preferEnglish) "Released" else "Вышел"
    AnimeReleaseStatus.ANNOUNCEMENT -> if (preferEnglish) "Announcement" else "Анонс"
    AnimeReleaseStatus.UNKNOWN -> if (preferEnglish) "Unknown" else "Неизвестно"
}

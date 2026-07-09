package org.akkirrai.hibiki.core.source

import org.akkirrai.animeresolver.model.AnimeTitle
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


package org.akkirrai.hibiki.shared.library

import org.akkirrai.hibiki.shared.settings.LanguageMode

fun isRussianLibraryLanguage(
    languageMode: LanguageMode,
    systemLanguage: String,
): Boolean = when (languageMode) {
    LanguageMode.RUSSIAN -> true
    LanguageMode.ENGLISH -> false
    LanguageMode.SYSTEM -> systemLanguage.lowercase() == "ru"
}

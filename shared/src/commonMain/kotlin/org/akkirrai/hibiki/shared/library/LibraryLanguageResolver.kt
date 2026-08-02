package org.akkirrai.hibiki.shared.library

import org.akkirrai.hibiki.shared.settings.LanguageMode
import org.akkirrai.hibiki.shared.settings.resolveAppLanguageTag

fun isRussianLibraryLanguage(
    languageMode: LanguageMode,
    systemLanguage: String,
): Boolean = resolveAppLanguageTag(languageMode, systemLanguage) == "ru"

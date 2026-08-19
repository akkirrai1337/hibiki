package org.akkirrai.hibiki.library.ui

import org.akkirrai.hibiki.app.settings.LanguageMode
import org.akkirrai.hibiki.app.settings.resolveAppLanguageTag

fun isRussianLibraryLanguage(
    languageMode: LanguageMode,
    systemLanguage: String,
): Boolean = resolveAppLanguageTag(languageMode, systemLanguage) == "ru"

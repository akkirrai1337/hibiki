package org.akkirrai.hibiki.shared.library.ui

import org.akkirrai.hibiki.shared.settings.LanguageMode
import org.akkirrai.hibiki.shared.settings.resolveAppLanguageTag

fun isRussianLibraryLanguage(
    languageMode: LanguageMode,
    systemLanguage: String,
): Boolean = resolveAppLanguageTag(languageMode, systemLanguage) == "ru"

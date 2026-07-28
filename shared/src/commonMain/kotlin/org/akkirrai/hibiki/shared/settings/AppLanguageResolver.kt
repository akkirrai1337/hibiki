package org.akkirrai.hibiki.shared.settings

fun resolveAppLanguageTag(languageMode: LanguageMode, systemLanguage: String): String = when (languageMode) {
    LanguageMode.ENGLISH -> "en"
    LanguageMode.RUSSIAN -> "ru"
    LanguageMode.SYSTEM -> if (systemLanguage.lowercase() == "ru") "ru" else "en"
}

fun isEnglishAppLanguage(languageMode: LanguageMode, systemLanguage: String): Boolean =
    resolveAppLanguageTag(languageMode, systemLanguage) == "en"

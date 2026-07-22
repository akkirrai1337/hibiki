package org.akkirrai.hibiki.shared.settings

/** User-selected application language, independent from the host platform locale. */
enum class LanguageMode(val tag: String?) {
    SYSTEM(null),
    RUSSIAN("ru"),
    ENGLISH("en"),
}

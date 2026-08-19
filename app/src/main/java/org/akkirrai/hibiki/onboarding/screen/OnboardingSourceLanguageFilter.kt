package org.akkirrai.hibiki.onboarding

fun <T> filterOnboardingSourcesByLanguage(
    sources: List<T>,
    systemLanguage: String,
    russianTag: String,
    englishTag: String,
    languageTags: (T) -> Set<String>,
): List<T> {
    val normalizedLanguage = systemLanguage.lowercase()
    val preferredTag = if (setOf("ru", "uk", "be").any(normalizedLanguage::startsWith)) {
        russianTag
    } else {
        englishTag
    }
    return sources.filter { preferredTag in languageTags(it) }
}

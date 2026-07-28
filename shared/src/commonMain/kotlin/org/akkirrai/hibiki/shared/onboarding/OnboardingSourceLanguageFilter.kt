package org.akkirrai.hibiki.shared.onboarding

fun <T> filterOnboardingSourcesByLanguage(
    sources: List<T>,
    systemLanguage: String,
    russianTag: String,
    englishTag: String,
    languageTags: (T) -> Set<String>,
): List<T> {
    val preferredTag = if (systemLanguage.lowercase() in setOf("ru", "uk", "be")) {
        russianTag
    } else {
        englishTag
    }
    return sources.filter { preferredTag in languageTags(it) }
}

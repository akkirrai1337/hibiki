package org.akkirrai.hibiki.shared.onboarding

fun formatOnboardingSourceLanguageSummary(
    languageTags: Set<String>,
    russianTag: String,
    englishTag: String,
    russianEnglishLabel: String,
    russianLabel: String,
    englishLabel: String,
): String {
    val normalizedTags = languageTags.map(String::lowercase).toSet()
    return when {
        russianTag in normalizedTags && englishTag in normalizedTags -> russianEnglishLabel
        russianTag in normalizedTags -> russianLabel
        englishTag in normalizedTags -> englishLabel
        else -> languageTags.joinToString { it.uppercase() }
    }
}

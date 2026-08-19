package org.akkirrai.hibiki.onboarding

fun <T> includeSelectedOnboardingSource(
    allSources: List<T>,
    visibleSources: List<T>,
    selectedKey: String?,
    keyOf: (T) -> String,
): List<T> {
    val selected = allSources.firstOrNull { keyOf(it) == selectedKey }
    return if (selected != null && visibleSources.none { keyOf(it) == selectedKey }) {
        listOf(selected) + visibleSources
    } else {
        visibleSources
    }
}

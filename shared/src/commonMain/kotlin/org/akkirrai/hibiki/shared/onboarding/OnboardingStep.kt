package org.akkirrai.hibiki.shared.onboarding

enum class OnboardingStep {
    WELCOME,
    SOURCE,
    NOTIFICATIONS,
}

fun OnboardingStep.previous(): OnboardingStep? = when (this) {
    OnboardingStep.WELCOME -> null
    OnboardingStep.SOURCE -> OnboardingStep.WELCOME
    OnboardingStep.NOTIFICATIONS -> OnboardingStep.SOURCE
}

fun onboardingBackEnabled(step: OnboardingStep): Boolean = step != OnboardingStep.WELCOME

fun OnboardingStep.next(): OnboardingStep? = when (this) {
    OnboardingStep.WELCOME -> OnboardingStep.SOURCE
    OnboardingStep.SOURCE -> OnboardingStep.NOTIFICATIONS
    OnboardingStep.NOTIFICATIONS -> null
}

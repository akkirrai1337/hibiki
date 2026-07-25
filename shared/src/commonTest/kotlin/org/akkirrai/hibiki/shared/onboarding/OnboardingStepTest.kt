package org.akkirrai.hibiki.shared.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals

class OnboardingStepTest {
    @Test
    fun keepsStablePersistenceOrder() {
        assertEquals(listOf("WELCOME", "SOURCE", "NOTIFICATIONS"), OnboardingStep.entries.map(Enum<*>::name))
    }
}

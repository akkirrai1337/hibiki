package org.akkirrai.hibiki.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingStepTest {
    @Test
    fun keepsStablePersistenceOrder() {
        assertEquals(listOf("WELCOME", "SOURCE", "NOTIFICATIONS"), OnboardingStep.entries.map(Enum<*>::name))
    }

    @Test
    fun systemBackIsEnabledOnlyAfterWelcome() {
        assertFalse(onboardingBackEnabled(OnboardingStep.WELCOME))
        assertTrue(onboardingBackEnabled(OnboardingStep.SOURCE))
        assertTrue(onboardingBackEnabled(OnboardingStep.NOTIFICATIONS))
    }
}

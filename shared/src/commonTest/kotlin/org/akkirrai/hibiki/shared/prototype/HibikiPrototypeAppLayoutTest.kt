package org.akkirrai.hibiki.shared.prototype

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.akkirrai.hibiki.shared.navigation.AppDestination

class HibikiPrototypeAppLayoutTest {
    @Test
    fun settingsOwnsTheTopInsetForItsOverlayBackButton() {
        assertFalse(shouldApplyTopSystemInset(AppDestination.SETTINGS))
    }

    @Test
    fun regularDestinationsKeepTheRootTopInset() {
        assertTrue(shouldApplyTopSystemInset(AppDestination.HOME))
        assertTrue(shouldApplyTopSystemInset(AppDestination.PROFILE))
    }
}

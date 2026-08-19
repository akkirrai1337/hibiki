package org.akkirrai.hibiki.app.shell.navigation

import org.akkirrai.hibiki.app.shell.navigation.shouldApplyTopSystemInset

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.akkirrai.hibiki.app.navigation.AppDestination

class HibikiAppShellLayoutTest {
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

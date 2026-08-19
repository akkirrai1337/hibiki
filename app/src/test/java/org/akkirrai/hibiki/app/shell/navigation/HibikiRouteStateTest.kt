package org.akkirrai.hibiki.app.shell.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.akkirrai.hibiki.player.model.WatchSource
import org.akkirrai.hibiki.app.navigation.AppDestination
import org.akkirrai.hibiki.app.navigation.AppRoute
import org.akkirrai.hibiki.app.shell.navigation.downloadModeEnabled
import org.akkirrai.hibiki.app.shell.navigation.shouldApplyTopSystemInset

class HibikiRouteStateTest {
    @Test
    fun downloadModeIsReadFromWatchRoutesOnly() {
        val source = WatchSource("source", "Source", episodeCount = 1)

        assertTrue(AppRoute.WatchSources("anime", downloadMode = true).downloadModeEnabled())
        assertTrue(AppRoute.Episodes(source, downloadMode = true).downloadModeEnabled())
        assertFalse(AppRoute.WatchSources("anime", downloadMode = false).downloadModeEnabled())
        assertFalse(AppRoute.Episodes(source, downloadMode = false).downloadModeEnabled())
        assertFalse(AppRoute.Player("source", "episode").downloadModeEnabled())
        assertFalse(null.downloadModeEnabled())
    }

    @Test
    fun topInsetIsDisabledOnlyForSettings() {
        assertFalse(shouldApplyTopSystemInset(AppDestination.SETTINGS))
        assertTrue(shouldApplyTopSystemInset(AppDestination.HOME))
        assertTrue(shouldApplyTopSystemInset(AppDestination.CATALOG))
    }
}

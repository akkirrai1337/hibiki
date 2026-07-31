package org.akkirrai.hibiki.shared.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.akkirrai.hibiki.shared.model.WatchSource

class AppRouteNavigationTest {
    private val source = WatchSource("source-1", "Dub", 12)

    @Test
    fun `details to sources to episodes to player`() {
        val state = AppNavigationState()
            .reduce(AppNavigationEvent.Navigate(AppRoute.Details("anime-1")))
            .reduce(AppNavigationEvent.Navigate(AppRoute.WatchSources("anime-1")))
            .reduce(AppNavigationEvent.Navigate(AppRoute.Episodes(source)))
            .reduce(AppNavigationEvent.Navigate(AppRoute.Player("source-1", "episode-1", 1.0)))

        assertEquals(
            listOf(
                AppRoute.Details("anime-1"),
                AppRoute.WatchSources("anime-1"),
                AppRoute.Episodes(source),
                AppRoute.Player("source-1", "episode-1", 1.0),
            ),
            state.backStack,
        )
    }

    @Test
    fun `back unwinds player episodes sources to details`() {
        val state = AppNavigationState().let {
            it.reduce(AppNavigationEvent.Navigate(AppRoute.Details("anime-1")))
                .reduce(AppNavigationEvent.Navigate(AppRoute.WatchSources("anime-1")))
                .reduce(AppNavigationEvent.Navigate(AppRoute.Episodes(source)))
                .reduce(AppNavigationEvent.Navigate(AppRoute.Player("source-1", "episode-1")))
        }

        assertEquals(AppRoute.Episodes(source), state.reduce(AppNavigationEvent.Back).currentRoute)
        assertEquals(
            AppRoute.WatchSources("anime-1"),
            state.reduce(AppNavigationEvent.Back).reduce(AppNavigationEvent.Back).currentRoute,
        )
        assertEquals(
            AppRoute.Details("anime-1"),
            state.reduce(AppNavigationEvent.Back)
                .reduce(AppNavigationEvent.Back)
                .reduce(AppNavigationEvent.Back)
                .currentRoute,
        )
    }

    @Test
    fun `back and dismiss close overlays before routes`() {
        val state = AppNavigationState()
            .reduce(AppNavigationEvent.Navigate(AppRoute.Details("anime-1")))
            .reduce(AppNavigationEvent.PresentOverlay(AppOverlay.Playlist))
            .reduce(AppNavigationEvent.PresentOverlay(AppOverlay.Sheet("queue")))

        val dismissed = state.reduce(AppNavigationEvent.DismissOverlay)
        assertEquals(listOf(AppOverlay.Playlist), dismissed.overlays)
        assertEquals(dismissed, state.reduce(AppNavigationEvent.Back))
        assertEquals(AppRoute.Details("anime-1"), dismissed.reduce(AppNavigationEvent.Back).currentRoute)
    }

    @Test
    fun `transition keys are stable and identify route instances`() {
        val first = AppRoute.Player("source-1", "episode-1").transitionKey()
        val same = AppRoute.Player("source-1", "episode-1").transitionKey()
        val next = AppRoute.Player("source-1", "episode-2").transitionKey()

        assertEquals(first, same)
        assertTrue(first != next)
        assertEquals(350, AppTransitionSpec(first, next).durationMillis)
    }

    @Test
    fun `transition spec keeps route keys and distinguishes push from pop`() {
        val details = AppRoute.Details("anime-1")
        val sources = AppRoute.WatchSources("anime-1")

        val push = appTransitionSpec(details, sources, AppTransitionDirection.Forward)
        val pop = appTransitionSpec(sources, details, AppTransitionDirection.Pop)

        assertEquals(sources.transitionKey(), push.enterKey)
        assertEquals(details.transitionKey(), push.exitKey)
        assertEquals(AppTransitionDirection.Forward, push.direction)
        assertEquals(AppTransitionDirection.Pop, pop.direction)
        assertEquals(AppTransitionSpec.DefaultDurationMillis, pop.durationMillis)
    }

    @Test
    fun `top level profile and library transitions use destination identities`() {
        val details = AppRoute.Details("anime-1")
        val profile = AppRoute.TopLevel(AppTopLevelDestination.PROFILE)
        val library = AppRoute.TopLevel(AppTopLevelDestination.LIBRARY)

        val profileSpec = appTransitionSpec(details, profile, AppTransitionDirection.Forward)
        val libraryPopSpec = appTransitionSpec(profile, library, AppTransitionDirection.Pop)

        assertEquals(AppTransitionKey("top-level", "profile"), profileSpec.enterKey)
        assertEquals(details.transitionKey(), profileSpec.exitKey)
        assertEquals(AppTransitionKey("top-level", "library"), libraryPopSpec.enterKey)
        assertEquals(profile.transitionKey(), libraryPopSpec.exitKey)
        assertEquals(AppTransitionDirection.Pop, libraryPopSpec.direction)
    }
}

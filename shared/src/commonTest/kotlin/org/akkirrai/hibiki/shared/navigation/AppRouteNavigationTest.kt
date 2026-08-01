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
        assertEquals(AppRoute.Player("source-1", "episode-1", 1.0), state.currentWatchRoute)
        assertEquals(source, state.selectedWatchSource)
        assertEquals(
            AppRoute.Player("source-1", "episode-1", 1.0).transitionKey(),
            state.currentTransitionKey,
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
    fun `details overlay dismisses before entering watch flow`() {
        val details = AppNavigationState()
            .reduce(AppNavigationEvent.Navigate(AppRoute.Details("anime-1")))
            .reduce(AppNavigationEvent.PresentOverlay(AppOverlay.DetailsTitleSheet))

        val detailsAfterDismiss = details.reduce(AppNavigationEvent.Back)
        val player = detailsAfterDismiss
            .reduce(AppNavigationEvent.Navigate(AppRoute.WatchSources("anime-1")))
            .reduce(AppNavigationEvent.Navigate(AppRoute.Episodes(source)))
            .reduce(AppNavigationEvent.Navigate(AppRoute.Player("source-1", "episode-1")))

        assertEquals(AppRoute.Details("anime-1"), detailsAfterDismiss.currentRoute)
        assertEquals(emptyList(), detailsAfterDismiss.overlays)
        assertEquals(AppRoute.Episodes(source), player.reduce(AppNavigationEvent.Back).currentRoute)
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
    fun `library filter sheet backs before leaving library`() {
        val filterSheet = AppOverlay.Sheet("library-filter")
        val state = AppNavigationState()
            .reduce(AppNavigationEvent.SelectTopLevel(AppTopLevelDestination.LIBRARY))
            .reduce(AppNavigationEvent.PresentOverlay(filterSheet))

        val dismissed = state.reduce(AppNavigationEvent.Back)

        assertEquals(emptyList(), dismissed.overlays)
        assertEquals(AppRoute.TopLevel(AppTopLevelDestination.LIBRARY), dismissed.currentRoute)
    }

    @Test
    fun `related details back returns to previous details`() {
        val state = AppNavigationState()
            .reduce(AppNavigationEvent.Navigate(AppRoute.Details("anime-1")))
            .reduce(AppNavigationEvent.Navigate(AppRoute.Details("anime-2")))

        assertEquals(AppRoute.Details("anime-2"), state.currentRoute)
        assertEquals(AppRoute.Details("anime-1"), state.reduce(AppNavigationEvent.Back).currentRoute)
    }

    @Test
    fun `settings route backs to profile root`() {
        val state = AppNavigationState()
            .reduce(AppNavigationEvent.SelectTopLevel(AppTopLevelDestination.PROFILE))
            .reduce(AppNavigationEvent.Navigate(AppRoute.Settings))

        assertEquals(AppRoute.Settings, state.currentRoute)
        assertEquals(
            AppRoute.TopLevel(AppTopLevelDestination.PROFILE),
            state.reduce(AppNavigationEvent.Back).currentRoute,
        )
    }

    @Test
    fun `back closes player overlay before player route`() {
        val state = AppNavigationState()
            .reduce(AppNavigationEvent.Navigate(AppRoute.Details("anime-1")))
            .reduce(AppNavigationEvent.Navigate(AppRoute.Player("source-1", "episode-1")))
            .reduce(AppNavigationEvent.PresentOverlay(AppOverlay.Playlist))

        val overlayDismissed = state.reduce(AppNavigationEvent.Back)

        assertEquals(emptyList(), overlayDismissed.overlays)
        assertEquals(AppRoute.Player("source-1", "episode-1"), overlayDismissed.currentRoute)
        assertEquals(AppRoute.Details("anime-1"), overlayDismissed.reduce(AppNavigationEvent.Back).currentRoute)
    }

    @Test
    fun `settings back returns to root before dismissing overlay and player`() {
        val state = AppNavigationState()
            .reduce(AppNavigationEvent.Navigate(AppRoute.Details("anime-1")))
            .reduce(AppNavigationEvent.Navigate(AppRoute.Player("source-1", "episode-1")))
            .reduce(AppNavigationEvent.PresentOverlay(AppOverlay.PlayerSettings))
            .reduce(
                AppNavigationEvent.SetPlayerSettingsDestination(
                    AppPlayerSettingsDestination.Quality,
                ),
            )

        val root = state.reduce(AppNavigationEvent.Back)
        assertEquals(AppPlayerSettingsDestination.Root, root.playerSettingsDestination)
        assertEquals(listOf(AppOverlay.PlayerSettings), root.overlays)
        assertEquals(AppRoute.Player("source-1", "episode-1"), root.currentRoute)

        val dismissed = root.reduce(AppNavigationEvent.Back)
        assertEquals(emptyList(), dismissed.overlays)
        assertEquals(AppRoute.Player("source-1", "episode-1"), dismissed.currentRoute)
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
    fun `episodes transition keys identify anime and download mode`() {
        val source = WatchSource("source-1", "Dub", 12)
        val first = AppRoute.Episodes(source, animeId = "anime-1").transitionKey()
        val otherAnime = AppRoute.Episodes(source, animeId = "anime-2").transitionKey()
        val download = AppRoute.Episodes(
            source,
            downloadMode = true,
            animeId = "anime-1",
        ).transitionKey()

        assertTrue(first != otherAnime)
        assertTrue(first != download)
    }

    @Test
    fun `watch sources transition keys identify download mode`() {
        val regular = AppRoute.WatchSources("anime-1").transitionKey()
        val downloads = AppRoute.WatchSources("anime-1", downloadMode = true).transitionKey()

        assertTrue(regular != downloads)
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

    @Test
    fun `replacing player episode keeps stack depth and back target`() {
        val state = AppNavigationState()
            .reduce(AppNavigationEvent.Navigate(AppRoute.Details("anime-1")))
            .reduce(AppNavigationEvent.Navigate(AppRoute.WatchSources("anime-1")))
            .reduce(AppNavigationEvent.Navigate(AppRoute.Episodes(source)))
            .reduce(AppNavigationEvent.Navigate(AppRoute.Player("source-1", "episode-1", 1.0)))

        val replaced = state.reduce(
            AppNavigationEvent.Replace(AppRoute.Player("source-1", "episode-2", 2.0)),
        )

        assertEquals(state.backStack.size, replaced.backStack.size)
        assertEquals(AppRoute.Player("source-1", "episode-2", 2.0), replaced.currentRoute)
        assertEquals(AppRoute.Episodes(source), replaced.reduce(AppNavigationEvent.Back).currentRoute)
        assertEquals(
            AppRoute.Player("source-1", "episode-2").transitionKey(),
            appTransitionSpec(
                AppRoute.Player("source-1", "episode-1"),
                replaced.currentRoute,
                AppTransitionDirection.Forward,
            ).enterKey,
        )
    }

    @Test
    fun `route first player resolve keeps details as back target`() {
        val loading = AppNavigationState()
            .reduce(AppNavigationEvent.Navigate(AppRoute.Details("anime-1")))
            .reduce(AppNavigationEvent.Navigate(AppRoute.Player("source-1", "episode-1")))
        val loaded = loading.reduce(
            AppNavigationEvent.Replace(AppRoute.Player("source-1", "episode-1", 1.0)),
        )

        assertEquals(loading.backStack.size, loaded.backStack.size)
        assertEquals(AppRoute.Details("anime-1"), loaded.reduce(AppNavigationEvent.Back).currentRoute)
    }

    @Test
    fun `route first player resolve keeps episodes as back target`() {
        val loading = AppNavigationState()
            .reduce(AppNavigationEvent.Navigate(AppRoute.Episodes(source)))
            .reduce(AppNavigationEvent.Navigate(AppRoute.Player("source-1", "episode-1")))
        val loaded = loading.reduce(
            AppNavigationEvent.Replace(AppRoute.Player("source-1", "episode-1", 1.0)),
        )

        assertEquals(AppRoute.Episodes(source), loaded.reduce(AppNavigationEvent.Back).currentRoute)
    }

    @Test
    fun `player back keeps watch anime only inside watch flow`() {
        assertTrue(shouldKeepWatchAnimeAfterPlayerBack(AppRoute.WatchSources("anime-1")))
        assertTrue(shouldKeepWatchAnimeAfterPlayerBack(AppRoute.Episodes(source)))
        assertTrue(!shouldKeepWatchAnimeAfterPlayerBack(AppRoute.Details("anime-1")))
        assertTrue(!shouldKeepWatchAnimeAfterPlayerBack(AppRoute.TopLevel(AppTopLevelDestination.HOME)))
    }
}

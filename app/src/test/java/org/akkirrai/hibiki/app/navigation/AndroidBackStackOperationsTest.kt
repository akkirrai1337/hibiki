package org.akkirrai.hibiki.app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidBackStackOperationsTest {
    @Test
    fun `push appends to the back stack`() {
        val backStack = listOf<AndroidNavigationRoute>(AndroidNavigationRoute.Home)
            .applyBackStackOp(AndroidBackStackOp.Push(AndroidNavigationRoute.Details("anime-1")))

        assertEquals(
            listOf(AndroidNavigationRoute.Home, AndroidNavigationRoute.Details("anime-1")),
            backStack,
        )
    }

    @Test
    fun `replace swaps the top entry without changing depth`() {
        val backStack = listOf(
            AndroidNavigationRoute.Home,
            AndroidNavigationRoute.Player("source-1", "episode-1"),
        ).applyBackStackOp(AndroidBackStackOp.Replace(AndroidNavigationRoute.Player("source-1", "episode-2")))

        assertEquals(
            listOf(AndroidNavigationRoute.Home, AndroidNavigationRoute.Player("source-1", "episode-2")),
            backStack,
        )
    }

    @Test
    fun `a single available source replaces watch sources with episodes`() {
        val route = AndroidNavigationRoute.Episodes(
            animeId = "anime-1",
            sourceId = "source-1",
            sourceTitle = "Dub",
            episodeCount = 12,
            qualityLabel = null,
            sourceIsPriority = false,
        )

        assertEquals(
            AndroidBackStackOp.Replace(route),
            watchSourcesToEpisodesOp(availableSourceCount = 1, route = route),
        )
    }

    @Test
    fun `multiple available sources push episodes instead of replacing`() {
        val route = AndroidNavigationRoute.Episodes(
            animeId = "anime-1",
            sourceId = "source-1",
            sourceTitle = "Dub",
            episodeCount = 12,
            qualityLabel = null,
            sourceIsPriority = false,
        )

        assertEquals(
            AndroidBackStackOp.Push(route),
            watchSourcesToEpisodesOp(availableSourceCount = 2, route = route),
        )
    }

    @Test
    fun `opening the player from episodes pushes a new entry`() {
        val route = AndroidNavigationRoute.Player("source-1", "episode-1", 1.0)

        assertEquals(
            AndroidBackStackOp.Push(route),
            playerNavigationOp(currentTop = AndroidNavigationRoute.Episodes(
                animeId = "anime-1",
                sourceId = "source-1",
                sourceTitle = "Dub",
                episodeCount = 12,
                qualityLabel = null,
                sourceIsPriority = false,
            ), route = route),
        )
    }

    @Test
    fun `switching episodes while on the player replaces the current entry`() {
        val route = AndroidNavigationRoute.Player("source-1", "episode-2", 2.0)

        assertEquals(
            AndroidBackStackOp.Replace(route),
            playerNavigationOp(
                currentTop = AndroidNavigationRoute.Player("source-1", "episode-1", 1.0),
                route = route,
            ),
        )
    }

    @Test
    fun `episodes to player then player to player mirrors the legacy back stack shape`() {
        val episodes = AndroidNavigationRoute.Episodes(
            animeId = "anime-1",
            sourceId = "source-1",
            sourceTitle = "Dub",
            episodeCount = 12,
            qualityLabel = null,
            sourceIsPriority = false,
        )
        var backStack = listOf<AndroidNavigationRoute>(episodes)

        val firstPlayer = AndroidNavigationRoute.Player("source-1", "episode-1", 1.0)
        backStack = backStack.applyBackStackOp(playerNavigationOp(backStack.lastOrNull(), firstPlayer))
        assertEquals(listOf(episodes, firstPlayer), backStack)

        val secondPlayer = AndroidNavigationRoute.Player("source-1", "episode-2", 2.0)
        backStack = backStack.applyBackStackOp(playerNavigationOp(backStack.lastOrNull(), secondPlayer))
        assertEquals(listOf(episodes, secondPlayer), backStack)
    }
}

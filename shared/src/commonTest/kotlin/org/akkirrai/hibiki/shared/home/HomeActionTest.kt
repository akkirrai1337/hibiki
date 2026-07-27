package org.akkirrai.hibiki.shared.home

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.model.AnimeSearchFilters

class HomeActionTest {
    @Test
    fun carriesSearchIntentWithoutPlatformTypes() {
        assertEquals("naruto", (HomeAction.SearchQueryChanged("naruto")).query)
        assertEquals(
            AnimeSearchFilters(),
            (HomeAction.ApplySearchFilters(AnimeSearchFilters())).filters,
        )
    }

    @Test
    fun keepsLoadAndRefreshActionsDistinct() {
        assertEquals(HomeAction.Refresh, HomeAction.Refresh)
        assertEquals(HomeAction.LoadMoreTrending, HomeAction.LoadMoreTrending)
        assertEquals(HomeAction.LoadMoreRecentUpdates, HomeAction.LoadMoreRecentUpdates)
    }
}

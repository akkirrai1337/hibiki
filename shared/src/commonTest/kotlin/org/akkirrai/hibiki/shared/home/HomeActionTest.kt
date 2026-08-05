package org.akkirrai.hibiki.shared.home
import org.akkirrai.hibiki.shared.home.data.*
import org.akkirrai.hibiki.shared.home.model.*
import org.akkirrai.hibiki.shared.home.presentation.*
import org.akkirrai.hibiki.shared.home.screen.*
import org.akkirrai.hibiki.shared.home.state.*
import org.akkirrai.hibiki.shared.home.ui.*

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.search.model.AnimeSearchFilters

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

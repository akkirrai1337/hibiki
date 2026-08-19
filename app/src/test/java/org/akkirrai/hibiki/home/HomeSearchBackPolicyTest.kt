package org.akkirrai.hibiki.home
import org.akkirrai.hibiki.home.data.*
import org.akkirrai.hibiki.home.model.*
import org.akkirrai.hibiki.home.presentation.*
import org.akkirrai.hibiki.home.screen.*
import org.akkirrai.hibiki.home.state.*
import org.akkirrai.hibiki.home.ui.*

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeSearchBackPolicyTest {
    @Test
    fun imeDismissesBeforeSearch() {
        assertEquals(
            HomeSearchBackAction.DismissIme,
            homeSearchBackAction(isImeVisible = true, isSearchActive = true),
        )
    }

    @Test
    fun activeSearchClearsWhenImeIsHidden() {
        assertEquals(
            HomeSearchBackAction.ClearSearch,
            homeSearchBackAction(isImeVisible = false, isSearchActive = true),
        )
    }

    @Test
    fun inactiveHomeDoesNotHandleBack() {
        assertEquals(
            HomeSearchBackAction.None,
            homeSearchBackAction(isImeVisible = false, isSearchActive = false),
        )
    }
}

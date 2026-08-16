package org.akkirrai.hibiki.shared.home
import org.akkirrai.hibiki.shared.home.data.*
import org.akkirrai.hibiki.shared.home.model.*
import org.akkirrai.hibiki.shared.home.presentation.*
import org.akkirrai.hibiki.shared.home.screen.*
import org.akkirrai.hibiki.shared.home.state.*
import org.akkirrai.hibiki.shared.home.ui.*

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

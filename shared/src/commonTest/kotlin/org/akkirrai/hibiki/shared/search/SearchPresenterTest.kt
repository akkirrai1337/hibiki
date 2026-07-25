package org.akkirrai.hibiki.shared.search

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchPresenterTest {
    @Test
    fun updatesQuery() {
        val presenter = SearchPresenter()

        presenter.update { it.copy(query = "naruto") }

        assertEquals("naruto", presenter.state.value.query)
    }
}

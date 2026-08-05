package org.akkirrai.hibiki.desktop

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.catalog.AppCatalogFilterSheet
import org.akkirrai.hibiki.shared.catalog.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.shared.search.model.AnimeSearchFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SharedCatalogFilterComposeTest {
    @Test
    fun commonCatalogFilterApplyBridgesFiltersAndDismissesSheet() = runComposeUiTest {
        var appliedFilters: AnimeSearchFilters? = null
        var dismissed = false

        setContent {
            MaterialTheme {
                AppCatalogFilterSheet(
                    initialFilters = AnimeSearchFilters(),
                    filterCatalog = AnimeCatalogFilterCatalog(),
                    isFilterCatalogLoading = false,
                    onApply = { appliedFilters = it },
                    onDismissRequest = { dismissed = true },
                    unavailableLabel = "Unavailable",
                    typeTitle = "Type",
                    genresTitle = "Genres",
                    yearTitle = "Year",
                    yearAllLabel = "All years",
                    yearFromLabel = "From",
                    yearToLabel = "To",
                    statusTitle = "Status",
                    resetLabel = "Reset",
                    applyLabel = "Apply",
                    defaultYearRange = 2000..2026,
                    optionText = { it.title },
                    shape = RoundedCornerShape(28.dp),
                )
            }
        }

        onNodeWithText("Apply")
            .assertIsDisplayed()
            .performClick()
        waitForIdle()

        assertEquals(AnimeSearchFilters(), appliedFilters)
        assertTrue(dismissed)
    }
}

package org.akkirrai.hibiki.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.akkirrai.hibiki.shared.app.AppProductionRoot
import org.akkirrai.hibiki.shared.navigation.AppNavigationEvent
import org.akkirrai.hibiki.shared.navigation.AppTopLevelDestination
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SharedProductionRootComposeTest {
    @Test
    fun productionRootUsesCommonBottomNavigationContract() = runComposeUiTest {
        var navigationEvent: AppNavigationEvent? = null

        setContent {
            MaterialTheme {
                AppProductionRoot(
                    currentDestination = AppTopLevelDestination.HOME,
                    onNavigationEvent = { navigationEvent = it },
                ) { destination ->
                    Text(destination.route)
                }
            }
        }

        onNodeWithText("home")
            .assertIsDisplayed()
        onNodeWithText("Catalog")
            .assertIsDisplayed()
            .performClick()

        assertEquals(
            AppNavigationEvent.SelectTopLevel(AppTopLevelDestination.CATALOG),
            navigationEvent,
        )
    }
}

package org.akkirrai.hibiki.desktop

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class DesktopComposeTestHarnessTest {
    @Test
    fun semanticsAndClicksAreDeliveredToComposeDesktop() {
        var clicked = false
        runComposeUiTest {
            setContent {
                Button(onClick = { clicked = true }) {
                    Text("Open catalog")
                }
            }

            onNodeWithText("Open catalog")
                .assertIsDisplayed()
                .performClick()
        }

        assertTrue(clicked)
    }
}

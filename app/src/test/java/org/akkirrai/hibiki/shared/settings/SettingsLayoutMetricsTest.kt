package org.akkirrai.hibiki.shared.settings

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsLayoutMetricsTest {
    @Test
    fun settingsContentStartsAfterTheOverlayBackButton() {
        assertEquals(128.dp, settingsContentTopPaddingWithBackButton(44.dp))
    }
}

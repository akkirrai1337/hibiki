package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerSettingsValueTest {
    @Test
    fun returnsSelectedLabelOrDefault() {
        val values = listOf(
            PlayerSettingsValue("a", "A", selected = false, onClick = {}),
            PlayerSettingsValue("b", "B", selected = true, onClick = {}),
        )

        assertEquals("B", values.firstSelectedLabelOrDefault())
        assertEquals("Fallback", values.map { it.copy(selected = false) }.firstSelectedLabelOrDefault("Fallback"))
    }
}

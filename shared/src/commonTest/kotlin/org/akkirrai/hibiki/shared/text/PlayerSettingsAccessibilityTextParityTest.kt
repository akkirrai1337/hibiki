package org.akkirrai.hibiki.shared.text

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.settings.LanguageMode

class PlayerSettingsAccessibilityTextParityTest {
    @Test
    fun playerSettingsLabelMatchesAndroidReference() {
        assertEquals(
            "Player settings",
            DefaultAppTextResolver(LanguageMode.ENGLISH).resolve(AppTextKey.PlayerSettings),
        )
        assertEquals(
            "Настройки плеера",
            DefaultAppTextResolver(LanguageMode.RUSSIAN).resolve(AppTextKey.PlayerSettings),
        )
    }
}

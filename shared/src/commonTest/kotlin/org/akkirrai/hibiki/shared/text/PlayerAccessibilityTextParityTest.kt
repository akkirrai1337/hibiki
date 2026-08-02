package org.akkirrai.hibiki.shared.text

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.settings.LanguageMode

class PlayerAccessibilityTextParityTest {
    @Test
    fun lockControlsLabelMatchesAndroidReference() {
        assertEquals(
            "Lock player controls",
            DefaultAppTextResolver(LanguageMode.ENGLISH).resolve(AppTextKey.PlayerLock),
        )
        assertEquals(
            "Заблокировать интерфейс плеера",
            DefaultAppTextResolver(LanguageMode.RUSSIAN).resolve(AppTextKey.PlayerLock),
        )
    }
}

package org.akkirrai.hibiki.app.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsSectionTest {
    @Test
    fun exposesStableKeys() {
        assertEquals("appearance", SettingsSection.Appearance.key)
        assertEquals("about", SettingsSection.About.key)
    }
}

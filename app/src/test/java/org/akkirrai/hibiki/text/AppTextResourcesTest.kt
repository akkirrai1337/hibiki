package org.akkirrai.hibiki.text

import kotlin.test.Test
import kotlin.test.assertEquals

class AppTextResourcesTest {
    @Test
    fun everyUiTextKeyHasAComposeResource() {
        assertEquals(AppTextKey.entries.toSet(), appTextResources.keys)
    }
}

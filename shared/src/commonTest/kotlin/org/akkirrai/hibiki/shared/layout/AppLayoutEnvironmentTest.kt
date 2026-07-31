package org.akkirrai.hibiki.shared.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import androidx.compose.ui.unit.dp

class AppLayoutEnvironmentTest {
    @Test
    fun `default environment is safe and inset based`() {
        val environment = AppLayoutEnvironment()

        assertEquals(0.dp, environment.topSystemInset)
        assertEquals(0.dp, environment.bottomSystemInset)
        assertEquals(AppNavigationBarMode.Inset, environment.navigationBarMode)
        assertEquals(AppScreenEdgePolicy.ContentSafe, environment.edgePolicy)
    }
}

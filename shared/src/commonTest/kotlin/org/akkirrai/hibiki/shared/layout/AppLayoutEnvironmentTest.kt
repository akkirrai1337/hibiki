package org.akkirrai.hibiki.shared.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import androidx.compose.ui.unit.dp

class AppLayoutEnvironmentTest {
    @Test
    fun `default environment is safe and inset based`() {
        val environment = AppLayoutEnvironment()

        assertEquals(false, environment.isProvided)
        assertEquals(0.dp, environment.topSystemInset)
        assertEquals(0.dp, environment.bottomSystemInset)
        assertEquals(AppNavigationBarMode.Inset, environment.navigationBarMode)
        assertEquals(AppScreenEdgePolicy.ContentSafe, environment.edgePolicy)
    }

    @Test
    fun `provided environment preserves host insets and edge policy`() {
        val environment = AppLayoutEnvironment(
            isProvided = true,
            topSystemInset = 24.dp,
            bottomSystemInset = 34.dp,
            navigationBarMode = AppNavigationBarMode.Overlay,
            edgePolicy = AppScreenEdgePolicy.EdgeToEdge,
        )

        assertEquals(24.dp, environment.topSystemInset)
        assertEquals(34.dp, environment.bottomSystemInset)
        assertEquals(AppNavigationBarMode.Overlay, environment.navigationBarMode)
        assertEquals(AppScreenEdgePolicy.EdgeToEdge, environment.edgePolicy)
    }
}

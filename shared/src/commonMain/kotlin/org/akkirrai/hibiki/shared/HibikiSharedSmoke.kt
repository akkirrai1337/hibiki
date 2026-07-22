package org.akkirrai.hibiki.shared

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.akkirrai.hibiki.shared.design.component.AppCard
import org.akkirrai.hibiki.shared.design.component.AppSection

/**
 * Small common UI entry point used only to prove that the shared CMP module
 * compiles for Android and Desktop before production screens are migrated.
 */
@Composable
fun HibikiSharedSmoke() {
    AppSection(title = "Hibiki") {
        AppCard {
            Text("Shared UI is ready")
        }
    }
}

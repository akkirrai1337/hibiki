package org.akkirrai.hibiki.shared

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import hibiki.shared.generated.resources.Res
import hibiki.shared.generated.resources.shared_ui_ready
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
            Text(stringResource(Res.string.shared_ui_ready))
        }
    }
}

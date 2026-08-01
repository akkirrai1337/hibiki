package org.akkirrai.hibiki.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.akkirrai.hibiki.shared.settings.AppDiscordAuthDialog
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SharedSettingsOverlayComposeTest {
    @Test
    fun commonDiscordDialogShowsAndBridgesBrowserSignIn() = runComposeUiTest {
        var browserSignInRequested = false

        setContent {
            MaterialTheme {
                AppDiscordAuthDialog(
                    initialToken = "",
                    isSignedIn = false,
                    statusText = "Signed out",
                    isChecking = false,
                    iconContent = { _: Modifier -> },
                    title = "Discord RPC",
                    manualTokenLabel = "Token",
                    invalidTokenLabel = "Invalid token",
                    disconnectLabel = "Disconnect",
                    browserSignInLabel = "Sign in browser",
                    cancelLabel = "Cancel",
                    applyLabel = "Apply",
                    onBrowserSignIn = { browserSignInRequested = true },
                    onDisconnect = {},
                    onDismiss = {},
                    onAuthenticate = { Result.success(Unit) },
                )
            }
        }

        onNodeWithText("Discord RPC")
            .assertIsDisplayed()
        onNodeWithText("Sign in browser")
            .assertIsDisplayed()
            .performClick()

        assertTrue(browserSignInRequested)
    }
}

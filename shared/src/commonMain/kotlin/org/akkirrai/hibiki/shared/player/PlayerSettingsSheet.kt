package org.akkirrai.hibiki.shared.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppPlayerSettingsSheet(
    destination: PlayerSettingsDestination,
    title: @Composable (PlayerSettingsDestination) -> String,
    onBack: () -> Unit,
    backContent: @Composable () -> Unit,
    content: LazyListScope.(PlayerSettingsDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        AnimatedContent(
            targetState = destination,
            transitionSpec = { playerSettingsPageTransition() },
            label = "PlayerSettingsPage",
        ) { targetDestination ->
            Column(modifier = Modifier.fillMaxWidth()) {
                if (targetDestination != PlayerSettingsDestination.Root) {
                    PlayerSettingsHeader(
                        title = title(targetDestination),
                        showBack = true,
                        onBack = onBack,
                        backContent = backContent,
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    userScrollEnabled = true,
                ) {
                    content(targetDestination)
                }
            }
        }
    }
}

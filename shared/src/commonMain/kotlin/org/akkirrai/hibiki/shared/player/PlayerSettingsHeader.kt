package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun PlayerSettingsHeader(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
    backContent: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = PlayerSettingsHeaderStartPadding,
                top = PlayerSettingsHeaderTopPadding,
                end = PlayerSettingsHeaderEndPadding,
                bottom = PlayerSettingsHeaderBottomPadding,
            ),
        horizontalArrangement = Arrangement.spacedBy(PlayerSettingsHeaderGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            backContent?.invoke() ?: Spacer(Modifier.width(PlayerSettingsHeaderBackSpacerWidth))
        } else {
            Spacer(Modifier.width(PlayerSettingsHeaderBackSpacerWidth))
        }
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

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
import androidx.compose.ui.unit.dp

@Composable
fun PlayerSettingsHeader(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
    backContent: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 2.dp, end = 18.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            backContent?.invoke() ?: Spacer(Modifier.width(8.dp))
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

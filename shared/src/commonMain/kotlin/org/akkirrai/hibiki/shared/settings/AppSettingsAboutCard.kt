package org.akkirrai.hibiki.shared.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AppSettingsAboutCard(
    appName: String,
    versionName: String,
    appIconContent: @Composable () -> Unit,
    githubIconContent: @Composable () -> Unit,
    onGitHubClick: () -> Unit,
) {
    val isDarkMode = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val background = if (isDarkMode) Color(0x190FFF66) else Color(0x59FFC0CB)
    val textColor = if (isDarkMode) Color(0xFF3BFF84) else Color(0xFFDA6482)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            appIconContent()
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = appName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
            )
            Text(
                text = "v$versionName",
                style = MaterialTheme.typography.labelMedium,
                color = textColor.copy(alpha = 0.75f),
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClick = onGitHubClick),
            contentAlignment = Alignment.Center,
        ) {
            githubIconContent()
        }
    }
}

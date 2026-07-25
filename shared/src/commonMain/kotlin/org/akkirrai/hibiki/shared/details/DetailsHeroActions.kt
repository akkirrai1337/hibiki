package org.akkirrai.hibiki.shared.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DetailsHeroActions(
    isInLibrary: Boolean,
    canWatch: Boolean,
    libraryLabel: String,
    watchLabel: String,
    libraryIcon: androidx.compose.ui.graphics.vector.ImageVector,
    primaryIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onLibraryClick: () -> Unit,
    onPrimaryClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onLibraryClick,
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = if (isInLibrary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = if (isInLibrary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(libraryIcon, libraryLabel, Modifier.size(28.dp)) }
        }
        OutlinedButton(
            onClick = onPrimaryClick,
            enabled = canWatch,
            modifier = Modifier.weight(1f).height(56.dp),
            shape = CircleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface, containerColor = Color.Transparent),
        ) {
            Icon(primaryIcon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(watchLabel, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium))
        }
    }
}

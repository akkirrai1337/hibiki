package org.akkirrai.hibiki.shared.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppDiscordAuthDialogActions(
    cancelLabel: String,
    applyLabel: String,
    cancelEnabled: Boolean,
    applyEnabled: Boolean,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            enabled = cancelEnabled,
        ) {
            Text(cancelLabel)
        }
        Button(
            onClick = onApply,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            enabled = applyEnabled,
        ) {
            Text(applyLabel)
        }
    }
}

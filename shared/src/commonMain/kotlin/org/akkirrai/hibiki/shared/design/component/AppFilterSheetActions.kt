package org.akkirrai.hibiki.shared.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import org.akkirrai.hibiki.shared.design.UiDimens

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppFilterSheetActions(
    resetLabel: String,
    applyLabel: String,
    resetIcon: ImageVector,
    applyIcon: ImageVector,
    onReset: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Spacer(modifier = Modifier.size(UiDimens.FilterSheetActionsTopGap))
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.spacedBy(UiDimens.FilterSheetActionsGap),
    ) {
        Button(
            onClick = onReset,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Icon(resetIcon, contentDescription = null, modifier = Modifier.size(UiDimens.FilterSheetActionIconSize))
            Spacer(modifier = Modifier.size(UiDimens.FilterSheetActionContentGap))
            Text(text = resetLabel, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.size(UiDimens.FilterSheetActionButtonGap))
        Button(onClick = onApply) {
            Icon(applyIcon, contentDescription = null, modifier = Modifier.size(UiDimens.FilterSheetActionIconSize))
            Spacer(modifier = Modifier.size(UiDimens.FilterSheetActionContentGap))
            Text(text = applyLabel, fontWeight = FontWeight.SemiBold)
        }
    }
}

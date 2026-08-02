package org.akkirrai.hibiki.shared.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.design.UiDimens

@Composable
fun AppHomeFilterCatalogState(
    isLoading: Boolean,
    unavailableLabel: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(UiDimens.FilterCatalogStateHeight),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Text(
                text = unavailableLabel,
                modifier = Modifier.padding(UiDimens.FilterCatalogUnavailablePadding),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

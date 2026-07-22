package org.akkirrai.hibiki.shared.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppFilterBottomSheet(
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    content: @Composable (Modifier) -> Unit,
) {
    AppModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier.fillMaxHeight(),
        shape = shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        scrimColor = Color.Black.copy(alpha = 0.56f),
        dragHandleContent = {},
    ) {
        content(
            Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        )
    }
}

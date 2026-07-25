package org.akkirrai.hibiki.shared.design.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
    shape: Shape,
    containerColor: Color,
    scrimColor: Color,
    tonalElevation: Dp = 0.dp,
    dragHandleBackgroundColor: Color = Color.Transparent,
    dragHandleContent: @Composable (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = shape,
        containerColor = containerColor,
        scrimColor = scrimColor,
        tonalElevation = tonalElevation,
        dragHandle = {
            Box(
                modifier = Modifier.fillMaxWidth().background(dragHandleBackgroundColor),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = sheetState.targetValue == androidx.compose.material3.SheetValue.Expanded,
                    label = "bottom_sheet_handle",
                    content = { expanded -> dragHandleContent(expanded) },
                )
            }
        },
        content = content,
    )
}

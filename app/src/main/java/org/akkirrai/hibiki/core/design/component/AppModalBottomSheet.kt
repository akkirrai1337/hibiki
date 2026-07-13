package org.akkirrai.hibiki.core.design.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
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
    shape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    scrimColor: Color = Color.Black.copy(alpha = 0.5f),
    tonalElevation: Dp = 0.dp,
    dragHandleBackgroundColor: Color = Color.Transparent,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .background(dragHandleBackgroundColor),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = sheetState.targetValue,
                    label = "bottom_sheet_handle",
                ) { targetValue ->
                    val (size, icon) = if (targetValue == SheetValue.Expanded) {
                        16.dp to Icons.Rounded.Close
                    } else {
                        20.dp to Icons.Rounded.KeyboardArrowUp
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(size),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        content = content,
    )
}

package org.akkirrai.hibiki.shared.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import org.akkirrai.hibiki.shared.design.UiDimens

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
        containerColor = MaterialTheme.colorScheme.background,
        scrimColor = Color.Black.copy(alpha = 0.56f),
        dragHandleContent = { expanded ->
            val handleColor = MaterialTheme.colorScheme.onSurfaceVariant
            Canvas(
                modifier = Modifier
                    .padding(UiDimens.FilterHandlePadding)
                    .size(UiDimens.FilterHandleSize),
            ) {
                val strokeWidth = UiDimens.FilterHandleStrokeWidth.toPx()
                val center = size.width / 2f
                if (expanded) {
                    val inset = size.width * UiDimens.FilterHandleExpandedInsetFraction
                    drawLine(handleColor, start = androidx.compose.ui.geometry.Offset(inset, inset), end = androidx.compose.ui.geometry.Offset(size.width - inset, size.height - inset), strokeWidth = strokeWidth, cap = StrokeCap.Round)
                    drawLine(handleColor, start = androidx.compose.ui.geometry.Offset(size.width - inset, inset), end = androidx.compose.ui.geometry.Offset(inset, size.height - inset), strokeWidth = strokeWidth, cap = StrokeCap.Round)
                } else {
                    drawLine(handleColor, start = androidx.compose.ui.geometry.Offset(UiDimens.FilterHandleCollapsedSideInsetPx, size.height * UiDimens.FilterHandleCollapsedOuterYFraction), end = androidx.compose.ui.geometry.Offset(center, size.height * UiDimens.FilterHandleCollapsedCenterYFraction), strokeWidth = strokeWidth, cap = StrokeCap.Round)
                    drawLine(handleColor, start = androidx.compose.ui.geometry.Offset(center, size.height * UiDimens.FilterHandleCollapsedCenterYFraction), end = androidx.compose.ui.geometry.Offset(size.width - UiDimens.FilterHandleCollapsedSideInsetPx, size.height * UiDimens.FilterHandleCollapsedOuterYFraction), strokeWidth = strokeWidth, cap = StrokeCap.Round)
                }
            }
        },
    ) {
        content(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        )
    }
}

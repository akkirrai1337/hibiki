package org.akkirrai.hibiki.design.component.filter

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import org.akkirrai.hibiki.design.UiDimens
import org.akkirrai.hibiki.design.component.modal.AppModalBottomSheet

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

/** Keeps filter-sheet drag settling identical to Android Material3 defaults on every host. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberAppFilterBottomSheetState(): SheetState {
    val density = LocalDensity.current
    return remember(density) {
        SheetState(
            skipPartiallyExpanded = false,
            positionalThreshold = { with(density) { FilterSheetPositionalThreshold.toPx() } },
            velocityThreshold = { with(density) { FilterSheetVelocityThreshold.toPx() } },
        )
    }
}

@Composable
fun AppFilterSheetContentContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .fillMaxWidth()
            .padding(horizontal = UiDimens.FilterSheetHorizontalPadding)
            .padding(bottom = UiDimens.FilterSheetBottomPadding),
    ) {
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppFilterSheetActions(
    resetLabel: String,
    applyLabel: String,
    resetIcon: Painter,
    applyIcon: Painter,
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
            Icon(painter = resetIcon, contentDescription = null, modifier = Modifier.size(UiDimens.FilterSheetActionIconSize))
            Spacer(modifier = Modifier.size(UiDimens.FilterSheetActionContentGap))
            Text(text = resetLabel, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.size(UiDimens.FilterSheetActionButtonGap))
        Button(onClick = onApply) {
            Icon(painter = applyIcon, contentDescription = null, modifier = Modifier.size(UiDimens.FilterSheetActionIconSize))
            Spacer(modifier = Modifier.size(UiDimens.FilterSheetActionContentGap))
            Text(text = applyLabel, fontWeight = FontWeight.SemiBold)
        }
    }
}

private val FilterSheetPositionalThreshold = 56.dp
private val FilterSheetVelocityThreshold = 125.dp

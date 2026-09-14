package org.akkirrai.hibiki.core.design.component

import android.content.Context
import android.os.Build
import android.view.RoundedCorner
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    scrimColor: Color = Color.Black.copy(alpha = 0.5f),
    tonalElevation: Dp = 0.dp,
    dragHandleBackgroundColor: Color = Color.Transparent,
    showDragHandle: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val resolvedShape = shape ?: rememberDeviceScreenTopCornerShape()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = resolvedShape,
        containerColor = containerColor,
        scrimColor = scrimColor,
        tonalElevation = tonalElevation,
        dragHandle = if (showDragHandle) {
            {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(dragHandleBackgroundColor),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        // The cross previews that releasing the current downward drag will close
                        // the sheet. The arrow remains visible while the sheet stays open.
                        targetState = sheetState.targetValue == SheetValue.Hidden,
                        // A fixed cell: the two icons differ in size, and letting the handle resize
                        // (AnimatedContent animates its size by default) changed the sheet's height
                        // mid-drag. ModalBottomSheet recomputes its anchors from that height every
                        // frame, so a short sheet visibly jerked while being swiped down.
                        modifier = Modifier
                            .padding(8.dp)
                            .size(20.dp),
                        contentAlignment = Alignment.Center,
                        label = "bottom_sheet_handle",
                    ) { willClose ->
                        val (size, icon) = if (willClose) {
                            16.dp to Icons.Rounded.Close
                        } else {
                            20.dp to Icons.Rounded.KeyboardArrowUp
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(size),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            null
        },
        content = content,
    )
}

@Composable
private fun rememberDeviceScreenTopCornerShape(): Shape {
    val context = LocalContext.current
    val density = LocalDensity.current
    val cornerRadiusPx = remember(context) { context.deviceScreenCornerRadiusPx() }
    val cornerRadius = with(density) { cornerRadiusPx.toDp() }
    return remember(cornerRadius) {
        RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)
    }
}

private fun Context.deviceScreenCornerRadiusPx(): Int {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return 0
    return getSystemService(WindowManager::class.java)
        ?.currentWindowMetrics
        ?.windowInsets
        ?.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)
        ?.radius
        ?: 0
}

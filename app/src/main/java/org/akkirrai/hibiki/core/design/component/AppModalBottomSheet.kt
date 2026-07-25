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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.foundation.shape.RoundedCornerShape
import org.akkirrai.hibiki.shared.design.component.AppModalBottomSheet as SharedModalBottomSheet

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
    content: @Composable ColumnScope.() -> Unit,
) {
    SharedModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
        shape = shape ?: rememberDeviceScreenTopCornerShape(),
        containerColor = containerColor,
        scrimColor = scrimColor,
        tonalElevation = tonalElevation,
        dragHandleBackgroundColor = dragHandleBackgroundColor,
        dragHandleContent = { expanded ->
            val (handleSize, icon) = if (expanded) 16.dp to Icons.Rounded.Close else 20.dp to Icons.Rounded.KeyboardArrowUp
            Icon(icon, null, modifier = Modifier.padding(8.dp).size(handleSize), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        content = content,
    )
}

@Composable
fun rememberDeviceScreenTopCornerShape(): Shape {
    val context = LocalContext.current
    val density = LocalDensity.current
    val cornerRadiusPx = remember(context) { context.deviceScreenCornerRadiusPx() }
    val cornerRadius = with(density) { cornerRadiusPx.toDp() }
    return remember(cornerRadius) { RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius) }
}

private fun Context.deviceScreenCornerRadiusPx(): Int {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return 0
    return getSystemService(WindowManager::class.java)?.currentWindowMetrics?.windowInsets
        ?.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)?.radius ?: 0
}

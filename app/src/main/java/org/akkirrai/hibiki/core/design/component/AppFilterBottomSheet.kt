package org.akkirrai.hibiki.core.design.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import org.akkirrai.hibiki.shared.design.component.AppFilterBottomSheet as SharedFilterBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppFilterBottomSheet(
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = rememberDeviceScreenTopCornerShape(),
    content: @Composable (Modifier) -> Unit,
) = SharedFilterBottomSheet(sheetState, onDismissRequest, modifier, shape, content)

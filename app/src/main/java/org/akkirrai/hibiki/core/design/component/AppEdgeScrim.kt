package org.akkirrai.hibiki.core.design.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.shared.design.component.AppBottomScrim as SharedBottomScrim
import org.akkirrai.hibiki.shared.design.component.AppEdgeScrimDefaults as SharedDefaults
import org.akkirrai.hibiki.shared.design.component.AppTopScrim as SharedTopScrim

typealias AppEdgeScrimDefaults = SharedDefaults

@Composable
fun AppTopScrim(modifier: Modifier = Modifier, height: Dp = AppEdgeScrimDefaults.TopHeight, brush: Brush = AppEdgeScrimDefaults.topBrush()) =
    SharedTopScrim(modifier, height, brush)

@Composable
fun AppBottomScrim(modifier: Modifier = Modifier, height: Dp = AppEdgeScrimDefaults.BottomHeight, brush: Brush = AppEdgeScrimDefaults.bottomBrush()) =
    SharedBottomScrim(modifier, height, brush)

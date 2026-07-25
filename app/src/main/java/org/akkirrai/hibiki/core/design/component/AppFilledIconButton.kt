package org.akkirrai.hibiki.core.design.component

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import org.akkirrai.hibiki.shared.design.component.AppFilledIconButton as SharedFilledIconButton
import org.akkirrai.hibiki.shared.design.component.AppFilledIconButtonStyle as SharedFilledIconButtonStyle

typealias AppFilledIconButtonStyle = SharedFilledIconButtonStyle

@Composable
fun AppFilledIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    style: AppFilledIconButtonStyle = AppFilledIconButtonStyle.Surface,
    content: @Composable () -> Unit,
) = SharedFilledIconButton(onClick, modifier, enabled, shape, style, content)

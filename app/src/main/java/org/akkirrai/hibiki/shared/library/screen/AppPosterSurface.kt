package org.akkirrai.hibiki.shared.library.screen
import org.akkirrai.hibiki.shared.library.*

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.design.component.surface.AppTonalSurface

@Composable
fun AppPosterSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    AppTonalSurface(
        modifier = modifier,
        shape = RoundedCornerShape(LibraryPosterSurfaceCornerRadius),
        content = content,
    )
}

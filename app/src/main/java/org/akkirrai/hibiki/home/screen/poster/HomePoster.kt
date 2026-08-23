package org.akkirrai.hibiki.home.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.design.component.surface.AppTonalSurface

@Composable
fun AppHomePoster(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    AppTonalSurface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        content = content,
    )
}

@Composable
fun AppHomePosterPlaceholder(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

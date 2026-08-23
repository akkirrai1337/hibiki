package org.akkirrai.hibiki.details.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

@Composable
fun AppDetailsContentList(
    state: LazyListState,
    bottomContentPadding: Dp,
    additionalBottomPadding: Dp,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = state,
        overscrollEffect = null,
        contentPadding = PaddingValues(
            bottom = bottomContentPadding + additionalBottomPadding,
        ),
        content = content,
    )
}

@Composable
fun DetailsNestedScrollableContent(
    modifier: Modifier = Modifier,
    gradientSize: Dp = DetailsNestedScrollableGradientSize,
    gradientColor: Color,
    content: @Composable (Modifier) -> Unit,
) {
    Box(modifier) {
        content(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = gradientSize),
        )
        Box(
            modifier = Modifier
                .height(gradientSize)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(colors = listOf(gradientColor, Color.Transparent))),
        )
        Box(
            modifier = Modifier
                .height(gradientSize)
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(colors = listOf(Color.Transparent, gradientColor))),
        )
    }
}

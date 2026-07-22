package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.design.component.AppBackButton

@Composable
fun WatchScreenScaffold(
    onBackClick: () -> Unit,
    backEnabled: Boolean,
    backIconContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding(),
    ) {
        content()
        AppBackButton(
            onClick = onBackClick,
            iconContent = backIconContent,
            enabled = backEnabled,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = UiDimens.ScreenPadding, top = 8.dp),
        )
    }
}

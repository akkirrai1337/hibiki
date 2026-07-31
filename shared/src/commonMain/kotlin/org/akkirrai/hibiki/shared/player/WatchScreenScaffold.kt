package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.design.component.AppBackButton
import org.akkirrai.hibiki.shared.layout.LocalAppLayoutEnvironment

@Composable
fun WatchScreenScaffold(
    onBackClick: () -> Unit,
    backEnabled: Boolean,
    backContentDescription: String?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(PaddingValues) -> Unit,
) {
    val density = LocalDensity.current
    val layoutEnvironment = LocalAppLayoutEnvironment.current
    val statusBarHeight = if (layoutEnvironment.isProvided) {
        layoutEnvironment.topSystemInset
    } else {
        with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    }
    val contentModifier = if (layoutEnvironment.isProvided) {
        when (layoutEnvironment.navigationBarMode) {
            org.akkirrai.hibiki.shared.layout.AppNavigationBarMode.Inset ->
                Modifier.padding(bottom = layoutEnvironment.bottomSystemInset)
            org.akkirrai.hibiki.shared.layout.AppNavigationBarMode.Overlay -> Modifier
        }
    } else {
        Modifier.navigationBarsPadding()
    }
    val backButtonModifier = if (layoutEnvironment.isProvided) {
        Modifier.padding(top = layoutEnvironment.topSystemInset)
    } else {
        Modifier.statusBarsPadding()
    }
    val contentPadding = PaddingValues(
        top = statusBarHeight + WatchScreenBackButtonTopPadding + WatchScreenBackButtonTouchSize + WatchScreenContentTopClearance,
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .then(contentModifier),
    ) {
        content(contentPadding)
        AppBackButton(
            onClick = onBackClick,
            contentDescription = backContentDescription,
            enabled = backEnabled,
            modifier = Modifier
                .align(Alignment.TopStart)
                .then(backButtonModifier)
                .padding(start = UiDimens.ScreenPadding, top = WatchScreenBackButtonTopPadding),
        )
    }
}

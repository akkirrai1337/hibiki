package org.akkirrai.hibiki.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.design.UiDimens
import org.akkirrai.hibiki.design.component.navigation.AppBackButton
import org.akkirrai.hibiki.layout.LocalAppLayoutEnvironment
import org.akkirrai.hibiki.layout.appBottomSystemInsetPadding
import org.akkirrai.hibiki.layout.appTopSystemInsetPadding

fun watchScreenContentPadding(statusBarHeight: Dp): PaddingValues = PaddingValues(
    start = WatchSourcesListHorizontalPadding,
    end = WatchSourcesListHorizontalPadding,
    top = statusBarHeight + WatchScreenBackButtonTopPadding + WatchScreenBackButtonIconSize + WatchScreenContentTopClearance,
    bottom = WatchSourcesListBottomPadding,
)

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
    val contentModifier = Modifier.appBottomSystemInsetPadding()
    val backButtonModifier = Modifier.appTopSystemInsetPadding()
    val contentPadding = watchScreenContentPadding(statusBarHeight)
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

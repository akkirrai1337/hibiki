package org.akkirrai.hibiki.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.design.UiDimens
import org.akkirrai.hibiki.layout.LocalAppLayoutEnvironment
import org.akkirrai.hibiki.layout.appBottomSystemInsetPadding
import org.akkirrai.hibiki.layout.appTopSystemInsetPadding

fun watchScreenContentPadding(statusBarHeight: Dp): PaddingValues = PaddingValues(
    start = WatchSourcesListHorizontalPadding,
    end = WatchSourcesListHorizontalPadding,
    top = statusBarHeight + WatchScreenBackButtonTopPadding + WatchScreenHeaderRowHeight + WatchScreenContentTopClearance,
    bottom = WatchSourcesListBottomPadding,
)

/**
 * Header row (back button + title, matching AppSettingsScreen's back-button-row style) instead of
 * the previously bare floating back button, so these screens aren't just an empty top area over
 * the list -- an optional [trailingContent] slot lets Episodes place its download toggle in the
 * same row instead of floating it separately over the list.
 */
@Composable
fun WatchScreenScaffold(
    onBackClick: () -> Unit,
    backEnabled: Boolean,
    backContentDescription: String?,
    title: String?,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
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
    val headerModifier = Modifier.appTopSystemInsetPadding()
    val contentPadding = watchScreenContentPadding(statusBarHeight)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .then(contentModifier),
    ) {
        content(contentPadding)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .then(headerModifier)
                .padding(
                    start = WatchScreenHeaderEdgePadding,
                    end = UiDimens.ScreenPadding,
                    top = WatchScreenBackButtonTopPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick, enabled = backEnabled) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = backContentDescription,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = title.orEmpty(),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = WatchScreenHeaderTitleStartPadding),
            )
            trailingContent?.invoke()
        }
    }
}

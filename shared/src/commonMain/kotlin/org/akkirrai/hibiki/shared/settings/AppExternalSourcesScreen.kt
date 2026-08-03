package org.akkirrai.hibiki.shared.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.shared.details.AppDetailsHeroOverlayBackButton
import org.akkirrai.hibiki.shared.layout.LocalAppLayoutEnvironment
import org.akkirrai.hibiki.shared.source.ExternalSourceRepositoryUiState

@Composable
fun AppExternalSourcesScreen(
    state: ExternalSourceRepositoryUiState,
    labels: ExternalSourceRepositorySectionLabels,
    bottomContentPadding: Dp,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit,
    backContentDescription: String,
    onAddRepository: (String) -> Unit,
    onRemoveRepository: (String) -> Unit,
    onRefresh: () -> Unit,
    onInstallPackage: (org.akkirrai.beakokit.api.SourceId) -> Unit = {},
    onRollbackPackage: (org.akkirrai.beakokit.api.SourceId) -> Unit = {},
) {
    val layoutEnvironment = LocalAppLayoutEnvironment.current
    val topSystemInset = if (layoutEnvironment.isProvided) {
        layoutEnvironment.topSystemInset
    } else {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    }
    Box(modifier = modifier.fillMaxSize()) {
        AppSettingsContentList(
            bottomContentPadding = bottomContentPadding,
            state = listState,
            topContentPadding = settingsContentTopPaddingWithBackButton(topSystemInset),
            modifier = Modifier.fillMaxSize(),
            content = {
                item(key = "external-source-repositories") {
                    ExternalSourceRepositorySection(
                        state = state,
                        labels = labels,
                        onAddRepository = onAddRepository,
                        onRemoveRepository = onRemoveRepository,
                        onRefresh = onRefresh,
                        onInstallPackage = onInstallPackage,
                        onRollbackPackage = onRollbackPackage,
                    )
                }
            },
        )
        AppDetailsHeroOverlayBackButton(
            onClick = onBackClick,
            contentDescription = backContentDescription,
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}

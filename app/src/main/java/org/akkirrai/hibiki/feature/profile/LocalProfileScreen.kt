package org.akkirrai.hibiki.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.AppFloatingHeader

@Composable
fun LocalProfileScreen(
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LocalProfileViewModel = viewModel(factory = LocalProfileViewModel.Factory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snapshot = remember(context.resources, state.data) {
        buildProfileSnapshot(context.resources, state.data)
    }
    Column(modifier = modifier.fillMaxSize()) {
        AppFloatingHeader(
            title = stringResource(R.string.local_profile_title),
            onBackClick = onBackClick,
            modifier = Modifier.fillMaxWidth().statusBarsPadding(),
            actions = {
                org.akkirrai.hibiki.core.design.component.AppFloatingIconButton(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.local_profile_settings),
                    onClick = onSettingsClick,
                )
            },
        )
        if (state.isLoading) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = UiDimens.ScreenPadding, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LocalProfileHeader()
                AnalyticsCard(snapshot)
                RecentLibraryCard(snapshot.recentLibraryItems)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

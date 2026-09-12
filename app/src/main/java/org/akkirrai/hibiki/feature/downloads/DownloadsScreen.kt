package org.akkirrai.hibiki.feature.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.AppMessageState
import org.akkirrai.hibiki.core.design.component.anime.VerticalAnimeListItem
import org.akkirrai.hibiki.core.model.WatchSource

private const val DOWNLOADS_REFRESH_INTERVAL_MS = 1_000L

@Composable
fun DownloadsScreen(
    onBackClick: () -> Unit,
    onOpenSource: (WatchSource) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = viewModel(factory = DownloadsViewModel.Factory(LocalContext.current)),
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(DOWNLOADS_REFRESH_INTERVAL_MS)
                viewModel.refresh()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = UiDimens.ScreenPadding,
                end = UiDimens.ScreenPadding,
                top = 84.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (uiState.entries.isEmpty()) {
                item {
                    AppMessageState(
                        title = stringResource(R.string.downloads_empty_title),
                        message = stringResource(R.string.downloads_empty_body),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 42.dp),
                        icon = Icons.Outlined.Download,
                    )
                }
            } else {
                items(items = uiState.entries, key = { it.anime.id }) { entry ->
                    DownloadedTitleRow(entry = entry, onOpenSource = onOpenSource)
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .height(56.dp),
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 18.dp)
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = stringResource(R.string.downloads_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun DownloadedTitleRow(
    entry: DownloadedTitleEntry,
    onOpenSource: (WatchSource) -> Unit,
) {
    var expanded by rememberSaveable(entry.anime.id) { mutableStateOf(false) }
    val singleSource = entry.sources.singleOrNull()

    Column {
        VerticalAnimeListItem(
            anime = entry.anime,
            onClick = {
                if (singleSource != null) {
                    onOpenSource(singleSource.source)
                } else {
                    expanded = !expanded
                }
            },
            metaContent = {
                Text(
                    text = downloadsSummaryText(entry),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
        if (expanded && singleSource == null) {
            Column(
                modifier = Modifier.padding(start = 118.dp, top = 2.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                entry.sources.forEach { sourceEntry ->
                    Surface(
                        onClick = { onOpenSource(sourceEntry.source) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = sourceEntry.source.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = sourceSummaryText(sourceEntry),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun downloadsSummaryText(entry: DownloadedTitleEntry): String {
    val completed = entry.sources.sumOf { it.completedCount }
    val active = entry.sources.sumOf { it.activeCount }
    val downloadedText = stringResource(R.string.downloads_episode_count, completed)
    return if (active > 0) {
        downloadedText + " · " + stringResource(R.string.downloads_active_count, active)
    } else {
        downloadedText
    }
}

@Composable
private fun sourceSummaryText(entry: DownloadedSourceEntry): String {
    val downloadedText = stringResource(R.string.downloads_episode_count, entry.completedCount)
    return if (entry.activeCount > 0) {
        downloadedText + " · " + stringResource(R.string.downloads_active_count, entry.activeCount)
    } else {
        downloadedText
    }
}

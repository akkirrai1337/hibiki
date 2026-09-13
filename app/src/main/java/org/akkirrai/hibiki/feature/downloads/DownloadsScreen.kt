package org.akkirrai.hibiki.feature.downloads

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandMore
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import org.akkirrai.hibiki.core.design.component.AnimeQuickAction
import org.akkirrai.hibiki.core.design.component.AnimeQuickActionsSheet
import org.akkirrai.hibiki.core.design.component.anime.VerticalAnimeListItem
import org.akkirrai.hibiki.core.model.WatchSource

private const val DOWNLOADS_REFRESH_INTERVAL_MS = 1_000L
private val DownloadsTopBarHeight = 56.dp

@Composable
fun DownloadsScreen(
    onBackClick: () -> Unit,
    onOpenSource: (WatchSource) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = viewModel(factory = DownloadsViewModel.Factory(LocalContext.current)),
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var titleWithActions by remember { mutableStateOf<DownloadedTitleEntry?>(null) }

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
                top = DownloadsTopBarHeight + 16.dp,
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
                    DownloadedTitleRow(
                        entry = entry,
                        onOpenSource = onOpenSource,
                        onLongClick = { titleWithActions = entry },
                    )
                }
            }
        }

        DownloadsTopBar(onBackClick = onBackClick)

        titleWithActions?.let { entry ->
            AnimeQuickActionsSheet(
                anime = entry.anime,
                actions = listOf(
                    AnimeQuickAction(
                        titleRes = R.string.downloads_remove_title_action,
                        descriptionRes = R.string.downloads_remove_title_action_description,
                        icon = Icons.Outlined.DeleteOutline,
                        isDestructive = true,
                        onClick = {
                            titleWithActions = null
                            viewModel.removeTitle(entry.anime.id)
                        },
                    ),
                ),
                onDismissRequest = { titleWithActions = null },
            )
        }
    }
}

@Composable
private fun BoxScope.DownloadsTopBar(onBackClick: () -> Unit) {
    Column(modifier = Modifier.align(Alignment.TopStart).fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DownloadsTopBarHeight)
                    .padding(start = 10.dp, end = 18.dp),
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
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)),
        )
    }
}

@Composable
private fun DownloadedTitleRow(
    entry: DownloadedTitleEntry,
    onOpenSource: (WatchSource) -> Unit,
    onLongClick: () -> Unit,
) {
    var expanded by rememberSaveable(entry.anime.id) { mutableStateOf(false) }
    val singleSource = entry.sources.singleOrNull()
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "downloads_chevron")

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
            onLongClick = onLongClick,
            metaContent = {
                DownloadsSummaryRow(
                    completedCount = entry.sources.sumOf { it.completedCount },
                    activeCount = entry.sources.sumOf { it.activeCount },
                )
            },
            trailingContent = if (singleSource == null) {
                {
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(chevronRotation),
                    )
                }
            } else {
                null
            },
        )
        if (expanded && singleSource == null) {
            Column(
                modifier = Modifier.padding(start = 118.dp, top = 2.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                entry.sources.forEach { sourceEntry ->
                    DownloadedSourceRow(
                        entry = sourceEntry,
                        onClick = { onOpenSource(sourceEntry.source) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadsSummaryRow(
    completedCount: Int,
    activeCount: Int,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DownloadsCountChip(
            icon = Icons.Outlined.Download,
            text = stringResource(R.string.downloads_episode_count, completedCount),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (activeCount > 0) {
            DownloadsCountChip(
                icon = null,
                text = stringResource(R.string.downloads_active_count, activeCount),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun DownloadsCountChip(
    icon: ImageVector?,
    text: String,
    color: Color,
    contentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(12.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DownloadedSourceRow(
    entry: DownloadedSourceEntry,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
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
                text = entry.source.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            DownloadsSummaryRow(completedCount = entry.completedCount, activeCount = entry.activeCount)
        }
    }
}

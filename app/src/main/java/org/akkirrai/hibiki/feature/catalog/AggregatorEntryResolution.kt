package org.akkirrai.hibiki.feature.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.design.component.AppModalBottomSheet
import org.akkirrai.hibiki.core.design.component.anime.PosterImage
import org.akkirrai.hibiki.core.metadata.decodeExternalEntryId
import org.akkirrai.hibiki.core.model.Anime

/**
 * The hinge of an aggregator-driven catalog: a card there names a provider entry, not a title of a
 * source, so opening one has to find the source's own title before the normal details screen can
 * take over.
 *
 * Most of the time none of this is seen - the entry resolves and the click behaves like any other.
 * When it does not, this is the whole answer to "why can I not watch this": a sheet that searches
 * the source and lets the right title be picked, rather than a details screen with no episodes on
 * it.
 *
 * Returns a click handler to give the catalog in place of its own, and renders its own progress and
 * sheet on top of the screen.
 */
@Composable
internal fun rememberEntryOpener(
    repository: CatalogRepository,
    onOpen: (Anime) -> Unit,
): (Anime) -> Unit {
    var resolving by remember { mutableStateOf<Anime?>(null) }
    var unresolved by remember { mutableStateOf<Anime?>(null) }

    resolving?.let { entry ->
        LaunchedEffect(entry.id) {
            val resolved = runCatching { repository.resolveEntry(entry) }.getOrNull()
            resolving = null
            if (resolved != null) onOpen(resolved) else unresolved = entry
        }
        ResolvingDialog()
    }

    unresolved?.let { entry ->
        ManualResolutionSheet(
            repository = repository,
            entry = entry,
            onDismiss = { unresolved = null },
            onPicked = { anime ->
                unresolved = null
                onOpen(anime)
            },
        )
    }

    return remember(repository, onOpen) {
        { anime ->
            // An ordinary source title opens the way it always has; only an entry needs resolving.
            if (decodeExternalEntryId(anime.id) == null) onOpen(anime) else resolving = anime
        }
    }
}

@Composable
private fun ResolvingDialog() {
    Dialog(onDismissRequest = {}) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.catalog_entry_resolving),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualResolutionSheet(
    repository: CatalogRepository,
    entry: Anime,
    onDismiss: () -> Unit,
    onPicked: (Anime) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember(entry.id) { mutableStateOf(entry.title) }
    var results by remember(entry.id) { mutableStateOf<List<Anime>>(emptyList()) }
    var searching by remember(entry.id) { mutableStateOf(false) }

    AppModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.catalog_entry_not_on_source, entry.title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.catalog_entry_not_on_source_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            )

            TextButton(
                onClick = {
                    val text = query.trim()
                    if (text.isEmpty() || searching) return@TextButton
                    searching = true
                    scope.launch {
                        results = runCatching { repository.searchSourceTitles(text) }.getOrDefault(emptyList())
                        searching = false
                    }
                },
                enabled = query.isNotBlank() && !searching,
            ) {
                Text(
                    stringResource(
                        if (searching) R.string.catalog_entry_searching else R.string.catalog_entry_search,
                    ),
                )
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results, key = { it.id }) { candidate ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                scope.launch {
                                    // Remembered as a manual binding, so this entry opens straight
                                    // to this title from now on.
                                    val opened = runCatching { repository.bindEntry(entry, candidate.id) }.getOrNull()
                                    if (opened != null) onPicked(opened)
                                }
                            }
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PosterImage(
                            primaryUrl = candidate.posterUrl,
                            contentDescription = null,
                            modifier = Modifier.size(width = 44.dp, height = 62.dp).clip(RoundedCornerShape(8.dp)),
                            placeholder = {},
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = candidate.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (candidate.subtitle.isNotBlank()) {
                                Text(
                                    text = candidate.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

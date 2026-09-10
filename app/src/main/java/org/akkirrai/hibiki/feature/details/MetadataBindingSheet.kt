package org.akkirrai.hibiki.feature.details

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
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
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
import kotlinx.coroutines.launch
import org.akkirrai.beakokit.metadata.ExternalMetadata
import org.akkirrai.beakokit.metadata.ExternalMetadataService
import org.akkirrai.beakokit.metadata.MetadataMatchRecord
import org.akkirrai.beakokit.metadata.MetadataProviderId
import org.akkirrai.beakokit.metadata.parseMetadataReference
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.design.component.AppModalBottomSheet
import org.akkirrai.hibiki.core.design.component.anime.PosterImage

/**
 * Which provider entry describes this title, and the way to change it.
 *
 * It exists because the matcher guesses: a source and an aggregator can only be compared on names,
 * years and types, and sequels, recaps and specials share all three often enough that some titles
 * land on the wrong entry. Without a way to correct that, the only recourse is turning the whole
 * feature off.
 *
 * Hidden unless asked for (see the Settings switch) - it answers a question most people never ask,
 * and the screen reads cleaner without it.
 */
@Composable
internal fun MetadataBindingRow(
    service: ExternalMetadataService,
    titleId: String,
    providers: List<MetadataProviderId>,
    preferredProvider: MetadataProviderId,
    onRebound: () -> Unit,
) {
    if (providers.isEmpty()) return
    var binding by remember(titleId) { mutableStateOf<MetadataMatchRecord?>(null) }
    var sheetOpen by remember(titleId) { mutableStateOf(false) }
    var reloads by remember(titleId) { mutableStateOf(0) }

    // Read after every rebind as well as on arrival: the row is the label on top of a binding this
    // sheet can change.
    LaunchedEffect(titleId, providers, reloads) {
        binding = service.bindingFor(titleId, providers)
    }

    val label: String = binding?.let { match: MetadataMatchRecord ->
        val provider = match.provider.ratingSource
        when {
            match.manual -> stringResource(R.string.details_metadata_described_manual, provider)
            match.confidencePercent != null ->
                stringResource(R.string.details_metadata_described_confidence, provider, match.confidencePercent!!)
            else -> stringResource(R.string.details_metadata_described, provider)
        }
    } ?: stringResource(R.string.details_metadata_not_matched)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { sheetOpen = true }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Public,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = binding?.let { "$label · #${it.externalId}" } ?: label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    if (sheetOpen) {
        MetadataPickerSheet(
            service = service,
            titleId = titleId,
            providers = providers,
            preferredProvider = preferredProvider,
            onDismiss = { sheetOpen = false },
            onChanged = {
                reloads++
                sheetOpen = false
                onRebound()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetadataPickerSheet(
    service: ExternalMetadataService,
    titleId: String,
    providers: List<MetadataProviderId>,
    preferredProvider: MetadataProviderId,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ExternalMetadata>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }

    AppModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.details_metadata_picker_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.details_metadata_picker_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.details_metadata_picker_placeholder)) },
            )

            TextButton(
                onClick = {
                    val text = query.trim()
                    if (text.isEmpty() || searching) return@TextButton
                    searching = true
                    scope.launch {
                        // A pasted link is resolved by id rather than searched for. That is not a
                        // shortcut: it is the only path that works while a provider's search is
                        // down, which is the state AniList's was in when this was written.
                        val reference = parseMetadataReference(text, preferredProvider)
                        results = if (reference != null) {
                            listOfNotNull(service.entryFor(reference))
                        } else {
                            service.search(text, providers)
                        }
                        searched = true
                        searching = false
                    }
                },
                enabled = query.isNotBlank() && !searching,
            ) {
                Text(
                    stringResource(
                        if (searching) R.string.details_metadata_picker_searching else R.string.details_metadata_picker_search,
                    ),
                )
            }

            if (searched && results.isEmpty() && !searching) {
                Text(
                    text = stringResource(R.string.details_metadata_picker_nothing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results, key = { "${it.provider}:${it.externalId}" }) { entry ->
                    EntryRow(entry) {
                        scope.launch {
                            service.setManualMatch(titleId, entry.provider, entry.externalId)
                            onChanged()
                        }
                    }
                }
            }

            TextButton(
                onClick = {
                    service.clearMatch(titleId)
                    onChanged()
                },
            ) {
                Text(stringResource(R.string.details_metadata_picker_reset))
            }
        }
    }
}

@Composable
private fun EntryRow(entry: ExternalMetadata, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosterImage(
            primaryUrl = entry.posterUrl,
            contentDescription = null,
            modifier = Modifier.size(width = 44.dp, height = 62.dp).clip(RoundedCornerShape(8.dp)),
            placeholder = {},
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = entry.englishName ?: entry.romajiName ?: entry.nativeName ?: "#${entry.externalId}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(entry.provider.ratingSource, entry.year?.toString(), entry.type).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

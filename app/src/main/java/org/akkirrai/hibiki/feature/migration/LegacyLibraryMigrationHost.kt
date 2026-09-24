package org.akkirrai.hibiki.feature.migration

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.migration.LegacyLibraryMatching
import org.akkirrai.hibiki.core.migration.LegacyLibraryMigration
import org.akkirrai.hibiki.core.migration.LegacyMigrationResult
import org.akkirrai.hibiki.core.migration.LegacySource
import org.akkirrai.hibiki.core.source.AnimeSourceDescriptor
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry

sealed interface LegacyMigrationState {
    data object Hidden : LegacyMigrationState
    data class Offer(val sources: List<LegacySource>, val chosen: Map<String, SourceId>) : LegacyMigrationState
    data class Running(val done: Int, val total: Int) : LegacyMigrationState
    data class Finished(val result: LegacyMigrationResult) : LegacyMigrationState
}

class LegacyLibraryMigrationViewModel(application: Application) : AndroidViewModel(application) {
    private val migration = LegacyLibraryMigration(application)
    private val _state = MutableStateFlow<LegacyMigrationState>(LegacyMigrationState.Hidden)
    val state: StateFlow<LegacyMigrationState> = _state.asStateFlow()

    init {
        if (!migration.dismissedForever) {
            viewModelScope.launch {
                val sources = runCatching { migration.scan() }.getOrDefault(emptyList())
                if (sources.isNotEmpty()) _state.value = LegacyMigrationState.Offer(sources, emptyMap())
            }
        }
    }

    fun choose(legacyId: String, target: SourceId?) {
        _state.update { current ->
            if (current !is LegacyMigrationState.Offer) return@update current
            current.copy(chosen = if (target == null) current.chosen - legacyId else current.chosen + (legacyId to target))
        }
    }

    fun start(targets: Map<String, SourceId>) {
        if (targets.isEmpty()) return
        _state.value = LegacyMigrationState.Running(0, 0)
        viewModelScope.launch {
            val result = migration.migrate(targets) { done, total ->
                _state.value = LegacyMigrationState.Running(done, total)
            }
            _state.value = LegacyMigrationState.Finished(result)
        }
    }

    /** "Not now": hidden for this launch, offered again next time. */
    fun later() {
        _state.value = LegacyMigrationState.Hidden
    }

    fun never() {
        migration.dismissedForever = true
        _state.value = LegacyMigrationState.Hidden
    }

    fun finish() {
        _state.value = LegacyMigrationState.Hidden
    }
}

/**
 * Offers to move the library from a retired scripted source to the same source installed as an APK
 * extension. Renders nothing unless the library still holds entries of a retired source.
 */
@Composable
fun LegacyLibraryMigrationHost() {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: LegacyLibraryMigrationViewModel = viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application),
    )
    val state by viewModel.state.collectAsState()
    when (val current = state) {
        LegacyMigrationState.Hidden -> Unit
        is LegacyMigrationState.Offer -> OfferDialog(current, viewModel)
        is LegacyMigrationState.Running -> RunningDialog(current)
        is LegacyMigrationState.Finished -> FinishedDialog(current.result, viewModel::finish)
    }
}

@Composable
private fun OfferDialog(offer: LegacyMigrationState.Offer, viewModel: LegacyLibraryMigrationViewModel) {
    // Read from the registry inside composition: extensions are installed and loaded while this
    // dialog is open, and the suggestions should appear the moment one is.
    val installed = AnimeSourceRegistry.sources
    val targets = offer.sources.mapNotNull { legacy ->
        val target = offer.chosen[legacy.id]?.let { id -> installed.firstOrNull { it.id == id } }
            ?: LegacyLibraryMatching.suggestTarget(legacy.name, installed) { it.info.name }
        target?.let { legacy.id to it.id }
    }.toMap()

    AlertDialog(
        onDismissRequest = viewModel::later,
        title = { Text(stringResource(R.string.migration_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.migration_message), style = MaterialTheme.typography.bodyMedium)
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(offer.sources, key = LegacySource::id) { legacy ->
                        LegacySourceRow(
                            legacy = legacy,
                            installed = installed,
                            selected = installed.firstOrNull { it.id == targets[legacy.id] },
                            onSelect = { viewModel.choose(legacy.id, it?.id) },
                        )
                    }
                }
                if (installed.isEmpty()) {
                    Text(
                        stringResource(R.string.migration_install_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.start(targets) }, enabled = targets.isNotEmpty()) {
                Text(stringResource(R.string.migration_start))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = viewModel::never) { Text(stringResource(R.string.migration_never)) }
                TextButton(onClick = viewModel::later) { Text(stringResource(R.string.migration_later)) }
            }
        },
    )
}

@Composable
private fun LegacySourceRow(
    legacy: LegacySource,
    installed: List<AnimeSourceDescriptor>,
    selected: AnimeSourceDescriptor?,
    onSelect: (AnimeSourceDescriptor?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(legacy.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            pluralStringResource(R.plurals.migration_entries, legacy.entryCount, legacy.entryCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = true }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selected?.info?.name ?: stringResource(R.string.migration_no_target),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.migration_skip_source)) },
                    onClick = { expanded = false; onSelect(null) },
                )
                installed.forEach { source ->
                    DropdownMenuItem(
                        text = { Text(source.info.name) },
                        onClick = { expanded = false; onSelect(source) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RunningDialog(running: LegacyMigrationState.Running) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.migration_running)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (running.total > 0) {
                    LinearProgressIndicator(
                        progress = { running.done.toFloat() / running.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${running.done} / ${running.total}", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun FinishedDialog(result: LegacyMigrationResult, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.migration_done_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.migration_done_message, result.migrated, result.total))
                if (result.unmatchedTitles.isNotEmpty()) {
                    Text(
                        stringResource(R.string.migration_unmatched),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        items(result.unmatchedTitles) { title ->
                            Text("• $title", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.migration_close)) } },
    )
}

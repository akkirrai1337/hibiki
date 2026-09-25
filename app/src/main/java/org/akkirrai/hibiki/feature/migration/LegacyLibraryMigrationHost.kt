package org.akkirrai.hibiki.feature.migration

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.graphics.Color
import org.akkirrai.hibiki.feature.sources.SourceExtensionsScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
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

/** Lets Settings ask the migration host to show the offer again after "Not now" or "Never". */
object LegacyMigrationRequests {
    internal val flow = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun reopen() {
        flow.tryEmit(Unit)
    }
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

    init {
        viewModelScope.launch {
            LegacyMigrationRequests.flow.collect {
                migration.dismissedForever = false
                val sources = runCatching { migration.scan() }.getOrDefault(emptyList())
                if (sources.isNotEmpty() && _state.value is LegacyMigrationState.Hidden) {
                    _state.value = LegacyMigrationState.Offer(sources, emptyMap())
                }
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
    var installOpen by remember { mutableStateOf(false) }
    when (val current = state) {
        LegacyMigrationState.Hidden -> Unit
        is LegacyMigrationState.Offer -> OfferDialog(current, viewModel, onInstall = { installOpen = true })
        is LegacyMigrationState.Running -> RunningDialog(current)
        is LegacyMigrationState.Finished -> FinishedDialog(current.result, viewModel::finish)
    }
    if (installOpen) InstallSourcesDialog(onClose = { installOpen = false })
}

/** The extension repositories, opened over the offer so a source can be installed without leaving it. */
@Composable
private fun InstallSourcesDialog(onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.migration_close))
                    }
                    Text(
                        stringResource(R.string.migration_install_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                SourceExtensionsScreen(showAvailable = true, modifier = Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfferDialog(
    offer: LegacyMigrationState.Offer,
    viewModel: LegacyLibraryMigrationViewModel,
    onInstall: () -> Unit,
) {
    var confirmNever by remember { mutableStateOf(false) }
    var pickerFor by remember { mutableStateOf<LegacySource?>(null) }
    // Read from the registry inside composition: extensions are installed and loaded while this
    // sheet is open, and the suggestions should appear the moment one is.
    val installed = AnimeSourceRegistry.sources
    val targets = offer.sources.mapNotNull { legacy ->
        val target = offer.chosen[legacy.id]?.let { id -> installed.firstOrNull { it.id == id } }
            ?: LegacyLibraryMatching.suggestTarget(legacy.name, installed) { it.info.name }
        target?.let { legacy.id to it.id }
    }.toMap()
    val titlesToMove = offer.sources.filter { it.id in targets }.sumOf { it.entryCount }

    ModalBottomSheet(
        onDismissRequest = viewModel::later,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Text(
                    stringResource(R.string.migration_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                stringResource(R.string.migration_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            offer.sources.forEach { legacy ->
                LegacySourceCard(
                    legacy = legacy,
                    selected = installed.firstOrNull { it.id == targets[legacy.id] },
                    onPick = { pickerFor = legacy },
                )
            }
            OutlinedButton(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Extension, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.migration_install_sources))
            }
            if (installed.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                        stringResource(R.string.migration_install_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Button(
                onClick = { viewModel.start(targets) },
                enabled = targets.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    if (titlesToMove > 0) {
                        stringResource(R.string.migration_start_count, titlesToMove)
                    } else {
                        stringResource(R.string.migration_start)
                    },
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = viewModel::later) { Text(stringResource(R.string.migration_later)) }
                TextButton(onClick = { confirmNever = true }) {
                    Text(stringResource(R.string.migration_never), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    pickerFor?.let { legacy ->
        SourcePickerSheet(
            legacy = legacy,
            installed = installed,
            selected = installed.firstOrNull { it.id == targets[legacy.id] },
            onSelect = { viewModel.choose(legacy.id, it?.id); pickerFor = null },
            onInstall = { pickerFor = null; onInstall() },
            onDismiss = { pickerFor = null },
        )
    }
    if (confirmNever) {
        AlertDialog(
            onDismissRequest = { confirmNever = false },
            icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
            title = { Text(stringResource(R.string.migration_never_title)) },
            text = { Text(stringResource(R.string.migration_never_message)) },
            confirmButton = {
                TextButton(onClick = { confirmNever = false; viewModel.never() }) {
                    Text(stringResource(R.string.migration_never_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmNever = false }) { Text(stringResource(R.string.migration_never_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourcePickerSheet(
    legacy: LegacySource,
    installed: List<AnimeSourceDescriptor>,
    selected: AnimeSourceDescriptor?,
    onSelect: (AnimeSourceDescriptor?) -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                stringResource(R.string.migration_pick_title, legacy.name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
            installed.forEach { source ->
                PickerRow(
                    selected = source.id == selected?.id,
                    onClick = { onSelect(source) },
                    icon = { SourceIcon(source, size = 32.dp) },
                    label = source.info.name,
                )
            }
            if (installed.isEmpty()) {
                Text(
                    stringResource(R.string.migration_install_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
            PickerRow(
                selected = false,
                onClick = { onSelect(null) },
                icon = {
                    Icon(Icons.Rounded.Block, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                },
                label = stringResource(R.string.migration_skip_source),
            )
            PickerRow(
                selected = false,
                onClick = onInstall,
                icon = {
                    Icon(Icons.Outlined.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                },
                label = stringResource(R.string.migration_install_sources),
                accent = true,
            )
        }
    }
}

@Composable
private fun PickerRow(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
    accent: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) { icon() }
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected || accent) FontWeight.SemiBold else FontWeight.Normal,
            color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun LegacySourceCard(
    legacy: LegacySource,
    selected: AnimeSourceDescriptor?,
    onPick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(legacy.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    pluralStringResource(R.plurals.migration_entries, legacy.entryCount, legacy.entryCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Rounded.ArrowDownward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        if (selected != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(14.dp),
                    )
                    .clickable(onClick = onPick)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (selected != null) SourceIcon(selected)
                Text(
                    text = selected?.info?.name ?: stringResource(R.string.migration_no_target),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Rounded.UnfoldMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SourceIcon(source: AnimeSourceDescriptor, size: androidx.compose.ui.unit.Dp = 24.dp) {
    AsyncImage(
        model = source.iconUrl,
        placeholder = painterResource(source.iconRes),
        error = painterResource(source.iconRes),
        contentDescription = null,
        modifier = Modifier.size(size).clip(CircleShape),
    )
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

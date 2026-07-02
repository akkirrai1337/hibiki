package org.akkirrai.hibiki.feature.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.pm.PackageInfoCompat
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.app.settings.AppPreferencesState
import org.akkirrai.hibiki.app.settings.LanguageMode
import org.akkirrai.hibiki.app.settings.ThemeMode
import org.akkirrai.hibiki.core.backup.HibikiBackupExporter
import org.akkirrai.hibiki.core.backup.HibikiBackupSelection
import org.akkirrai.hibiki.core.backup.InvalidBackupRecoveryKeyException
import org.akkirrai.hibiki.core.backup.MissingAccountSessionException
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.AppTonalSurface
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.log.PerfLogger
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = UiDimens.ScreenPadding,
    appPreferences: AppPreferences? = null,
    onCreateBackupDocument: ((String, (Uri?) -> Unit) -> Unit)? = null,
    onOpenBackupDocument: (((Uri?) -> Unit) -> Unit)? = null,
) {
    val context = LocalContext.current
    val preferencesState = if (appPreferences != null) {
        val state by appPreferences.state.collectAsState()
        state
    } else {
        AppPreferencesState()
    }

    val systemDarkTheme = isSystemInDarkTheme()
    val darkThemeEnabled = when (preferencesState.themeMode) {
        ThemeMode.SYSTEM -> systemDarkTheme
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    var showLanguageSheet by remember { mutableStateOf(false) }
    var showBackupSheet by remember { mutableStateOf(false) }
    var pendingBackupSelection by remember { mutableStateOf<HibikiBackupSelection?>(null) }
    var backupExportInProgress by remember { mutableStateOf(false) }
    var backupImportInProgress by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showImportRecoveryKeyDialog by remember { mutableStateOf(false) }
    var accountRecoveryKey by remember { mutableStateOf<String?>(null) }
    val appVersionText = remember(context) { appVersionLabel(context) }
    val backupExporter = remember(context) { HibikiBackupExporter(context) }
    val coroutineScope = rememberCoroutineScope()
    fun exportBackupToUri(uri: Uri?) {
        val selection = pendingBackupSelection
        if (uri == null || selection == null) {
            pendingBackupSelection = null
            return
        }

        coroutineScope.launch {
            backupExportInProgress = true
            runCatching { backupExporter.export(uri, selection) }
                .onSuccess { result ->
                    showBackupSheet = false
                    accountRecoveryKey = result.recoveryKey
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_backup_export_success),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                .onFailure { throwable ->
                    Toast.makeText(
                        context,
                        context.getString(
                            if (throwable is MissingAccountSessionException) {
                                R.string.settings_backup_account_missing
                            } else {
                                R.string.settings_backup_export_failed
                            },
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            pendingBackupSelection = null
            backupExportInProgress = false
        }
    }
    fun importBackupFromUri(
        uri: Uri?,
        recoveryKey: String? = null,
    ) {
        if (uri == null) {
            pendingImportUri = null
            return
        }

        coroutineScope.launch {
            backupImportInProgress = true
            runCatching { backupExporter.importBackup(uri, recoveryKey) }
                .onSuccess {
                    pendingImportUri = null
                    showImportRecoveryKeyDialog = false
                    showBackupSheet = false
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_backup_import_success),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                .onFailure { throwable ->
                    Toast.makeText(
                        context,
                        context.getString(
                            if (throwable is InvalidBackupRecoveryKeyException) {
                                R.string.settings_backup_import_invalid_key
                            } else {
                                R.string.settings_backup_import_failed
                            },
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            backupImportInProgress = false
        }
    }

    fun inspectBackupForImport(uri: Uri?) {
        if (uri == null) {
            pendingImportUri = null
            return
        }

        coroutineScope.launch {
            backupImportInProgress = true
            runCatching { backupExporter.inspect(uri) }
                .onSuccess { preview ->
                    if (preview.requiresRecoveryKey) {
                        pendingImportUri = uri
                        showImportRecoveryKeyDialog = true
                    } else {
                        runCatching { backupExporter.importBackup(uri) }
                            .onSuccess {
                                pendingImportUri = null
                                showBackupSheet = false
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.settings_backup_import_success),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.settings_backup_import_failed),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                    }
                }
                .onFailure {
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_backup_import_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            backupImportInProgress = false
        }
    }
    LaunchedEffect(Unit) {
        PerfLogger.mark("SettingsScreen composed")
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = UiDimens.ScreenPadding,
            top = 12.dp,
            end = UiDimens.ScreenPadding,
            bottom = bottomContentPadding
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            SettingsGroupCard(
                title = stringResource(R.string.settings_general)
            ) {
                SettingsSwitchItem(
                    icon = Icons.Outlined.DarkMode,
                    title = stringResource(R.string.settings_dark_theme),
                    subtitle = stringResource(
                        if (darkThemeEnabled) {
                            R.string.settings_theme_dark_summary
                        } else {
                            R.string.settings_theme_light_summary
                        }
                    ),
                    checked = darkThemeEnabled,
                    onCheckedChange = { enabled ->
                        appPreferences?.setThemeMode(
                            if (enabled) ThemeMode.DARK else ThemeMode.LIGHT
                        )
                    }
                )

                SettingsDivider()

                SettingsClickableItem(
                    icon = Icons.Outlined.Language,
                    title = stringResource(R.string.settings_language),
                    subtitle = languageModeLabel(preferencesState.languageMode),
                    onClick = { showLanguageSheet = true }
                )

                SettingsDivider()

                SettingsSwitchItem(
                    icon = Icons.Outlined.Refresh,
                    title = stringResource(R.string.settings_force_trending_slot_on_refresh),
                    subtitle = stringResource(
                        if (preferencesState.forceAdvanceTrendingSlotOnRefresh) {
                            R.string.settings_force_trending_slot_on_refresh_enabled_summary
                        } else {
                            R.string.settings_force_trending_slot_on_refresh_disabled_summary
                        }
                    ),
                    checked = preferencesState.forceAdvanceTrendingSlotOnRefresh,
                    onCheckedChange = { enabled ->
                        appPreferences?.setForceAdvanceTrendingSlotOnRefresh(enabled)
                    }
                )
            }
        }

        item {
            SettingsGroupCard(
                title = stringResource(R.string.settings_player)
            ) {
                SettingsSwitchItem(
                    icon = Icons.Outlined.SkipNext,
                    title = stringResource(R.string.settings_auto_skip_segments),
                    subtitle = stringResource(
                        if (preferencesState.autoSkipSegments) {
                            R.string.settings_auto_skip_segments_enabled_summary
                        } else {
                            R.string.settings_auto_skip_segments_disabled_summary
                        }
                    ),
                    checked = preferencesState.autoSkipSegments,
                    onCheckedChange = { enabled ->
                        appPreferences?.setAutoSkipSegments(enabled)
                    }
                )
            }
        }

        item {
            SettingsGroupCard(
                title = stringResource(R.string.settings_support)
            ) {
                SettingsClickableItem(
                    icon = Icons.Outlined.Download,
                    title = stringResource(R.string.settings_backup),
                    subtitle = stringResource(R.string.settings_backup_summary),
                    onClick = { showBackupSheet = true }
                )

                SettingsDivider()

                SettingsClickableItem(
                    icon = Icons.Outlined.Share,
                    title = stringResource(R.string.settings_export_logs),
                    subtitle = stringResource(R.string.settings_export_logs_summary),
                    onClick = {
                        AppLogger.shareLogs(context).onFailure {
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_export_logs_failed),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                )
            }
        }

        item {
            AppVersionText(text = appVersionText)
        }
    }

    if (showLanguageSheet) {
        LanguageBottomSheet(
            selectedMode = preferencesState.languageMode,
            onSelect = { mode ->
                appPreferences?.setLanguageMode(mode)
                showLanguageSheet = false
            },
            onDismiss = { showLanguageSheet = false }
        )
    }

    if (showBackupSheet) {
        BackupBottomSheet(
            exportInProgress = backupExportInProgress,
            importInProgress = backupImportInProgress,
            onExport = { selection ->
                pendingBackupSelection = selection
                val createBackupDocument = onCreateBackupDocument
                if (createBackupDocument == null) {
                    pendingBackupSelection = null
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_backup_export_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    createBackupDocument(
                        "hibiki-backup-${System.currentTimeMillis()}.hibiki-backup",
                        ::exportBackupToUri,
                    )
                }
            },
            onImport = {
                val openBackupDocument = onOpenBackupDocument
                if (openBackupDocument == null) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_backup_import_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    openBackupDocument(::inspectBackupForImport)
                }
            },
            onDismiss = {
                if (!backupExportInProgress && !backupImportInProgress) {
                    showBackupSheet = false
                }
            },
        )
    }

    accountRecoveryKey?.let { recoveryKey ->
        AccountRecoveryKeyDialog(
            recoveryKey = recoveryKey,
            onDismiss = { accountRecoveryKey = null },
        )
    }

    if (showImportRecoveryKeyDialog) {
        ImportRecoveryKeyDialog(
            importInProgress = backupImportInProgress,
            onImport = { recoveryKey ->
                importBackupFromUri(pendingImportUri, recoveryKey)
            },
            onDismiss = {
                if (!backupImportInProgress) {
                    pendingImportUri = null
                    showImportRecoveryKeyDialog = false
                }
            },
        )
    }
}

@Composable
private fun AppVersionText(
    text: String,
) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
        textAlign = TextAlign.Center,
    )
}

private fun appVersionLabel(context: Context): String {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    return context.getString(
        R.string.settings_app_version,
        packageInfo.versionName.orEmpty().ifBlank { "unknown" },
        PackageInfoCompat.getLongVersionCode(packageInfo),
    )
}

@Composable
private fun SettingsGroupCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsBaseItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        modifier = Modifier
            .clickable { onCheckedChange(!checked) }
            .fillMaxWidth(),
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsClickableItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    SettingsBaseItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

@Composable
private fun SettingsBaseItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon = icon)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        trailingContent?.invoke()
    }
}

@Composable
private fun SettingsIcon(
    icon: ImageVector
) {
    AppTonalSurface(
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 16.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupBottomSheet(
    exportInProgress: Boolean,
    importInProgress: Boolean,
    onExport: (HibikiBackupSelection) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selection by remember { mutableStateOf(HibikiBackupSelection()) }
    val busy = exportInProgress || importInProgress

    SettingsOptionsBottomSheet(
        title = stringResource(R.string.settings_backup),
        description = stringResource(R.string.settings_backup_sheet_description),
        onDismiss = onDismiss,
    ) {
        BackupOptionRow(
            title = stringResource(R.string.settings_backup_option_settings),
            description = stringResource(R.string.settings_backup_option_settings_summary),
            checked = selection.settings,
            enabled = !busy,
            onCheckedChange = { checked -> selection = selection.copy(settings = checked) },
        )
        BackupOptionRow(
            title = stringResource(R.string.settings_backup_option_library),
            description = stringResource(R.string.settings_backup_option_library_summary),
            checked = selection.library,
            enabled = !busy,
            onCheckedChange = { checked -> selection = selection.copy(library = checked) },
        )
        BackupOptionRow(
            title = stringResource(R.string.settings_backup_option_watch_progress),
            description = stringResource(R.string.settings_backup_option_watch_progress_summary),
            checked = selection.watchProgress,
            enabled = !busy,
            onCheckedChange = { checked -> selection = selection.copy(watchProgress = checked) },
        )
        BackupOptionRow(
            title = stringResource(R.string.settings_backup_option_account),
            description = stringResource(R.string.settings_backup_option_account_summary),
            checked = selection.account,
            enabled = !busy,
            onCheckedChange = { checked -> selection = selection.copy(account = checked) },
        )

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            enabled = selection.hasAnySelection && !busy,
            onClick = { onExport(selection) },
        ) {
            Text(
                text = stringResource(
                    if (exportInProgress) {
                        R.string.settings_backup_exporting
                    } else {
                        R.string.settings_backup_export
                    },
                ),
            )
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            onClick = onImport,
        ) {
            Icon(
                imageVector = Icons.Outlined.Upload,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = stringResource(
                    if (importInProgress) {
                        R.string.settings_backup_importing
                    } else {
                        R.string.settings_backup_import
                    },
                ),
            )
        }
    }
}

@Composable
private fun BackupOptionRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    AppTonalSurface(
        modifier = Modifier.fillMaxWidth(),
        onClick = { if (enabled) onCheckedChange(!checked) },
        shape = RoundedCornerShape(UiDimens.MediumCorner),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AccountRecoveryKeyDialog(
    recoveryKey: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.settings_backup_recovery_key_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.settings_backup_recovery_key_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                AppTonalSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(UiDimens.MediumCorner),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    SelectionContainer {
                        Text(
                            text = recoveryKey,
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
    )
}

@Composable
private fun ImportRecoveryKeyDialog(
    importInProgress: Boolean,
    onImport: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var recoveryKey by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.settings_backup_import_key_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.settings_backup_import_key_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = recoveryKey,
                    onValueChange = { recoveryKey = it },
                    enabled = !importInProgress,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(text = stringResource(R.string.settings_backup_import_key_label))
                    },
                )
            }
        },
        confirmButton = {
            Button(
                enabled = recoveryKey.isNotBlank() && !importInProgress,
                onClick = { onImport(recoveryKey.trim()) },
            ) {
                Text(text = stringResource(R.string.settings_backup_import))
            }
        },
        dismissButton = {
            TextButton(
                enabled = !importInProgress,
                onClick = onDismiss,
            ) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageBottomSheet(
    selectedMode: LanguageMode,
    onSelect: (LanguageMode) -> Unit,
    onDismiss: () -> Unit
) {
    SettingsOptionsBottomSheet(
        title = stringResource(R.string.settings_language),
        description = stringResource(R.string.settings_language_sheet_description),
        onDismiss = onDismiss
    ) {
        LanguageMode.entries.forEach { mode ->
            SettingsOptionRow(
                title = languageModeLabel(mode),
                description = languageModeDescription(mode),
                selected = mode == selectedMode,
                onClick = { onSelect(mode) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsOptionsBottomSheet(
    title: String,
    description: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = UiDimens.ScreenPadding,
                    end = UiDimens.ScreenPadding,
                    bottom = 28.dp
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))
                content()
            }
        )
    }
}

@Composable
private fun SettingsOptionRow(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    AppTonalSurface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(UiDimens.MediumCorner),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun languageModeLabel(mode: LanguageMode): String {
    return stringResource(languageModeTexts(mode).titleResId)
}

@Composable
private fun languageModeDescription(mode: LanguageMode): String {
    return stringResource(languageModeTexts(mode).descriptionResId)
}

private fun languageModeTexts(mode: LanguageMode): LanguageModeText {
    return when (mode) {
        LanguageMode.SYSTEM -> LanguageModeText(
            titleResId = R.string.settings_language_system,
            descriptionResId = R.string.settings_language_system_summary,
        )
        LanguageMode.RUSSIAN -> LanguageModeText(
            titleResId = R.string.settings_language_russian,
            descriptionResId = R.string.settings_language_russian_summary,
        )
        LanguageMode.ENGLISH -> LanguageModeText(
            titleResId = R.string.settings_language_english,
            descriptionResId = R.string.settings_language_english_summary,
        )
    }
}

private data class LanguageModeText(
    val titleResId: Int,
    val descriptionResId: Int,
)

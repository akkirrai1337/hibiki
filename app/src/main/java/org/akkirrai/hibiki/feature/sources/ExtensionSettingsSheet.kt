package org.akkirrai.hibiki.feature.sources

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.TwoStatePreference
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.design.component.AppModalBottomSheet
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry
import org.akkirrai.hibiki.feature.settings.ExternalMetadataSheetGroup
import org.akkirrai.hibiki.feature.settings.SettingsItemRow
import org.akkirrai.hibiki.feature.settings.SettingsItems
import org.akkirrai.hibiki.feature.settings.SettingsSwitchItem

/**
 * An Aniyomi extension settings, in the same sheet as the app option sheets. Extensions describe
 * them by filling a PreferenceScreen (setupPreferenceScreen) with androidx.preference objects and
 * Aniyomi persists them in the source_<id> SharedPreferences, which the extension reads back. The
 * preferences are built exactly that way, then drawn here with the app own rows instead of the
 * classic preference views.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionSettingsSheet(
    sourceId: SourceId,
    title: String,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val screen = remember(sourceId) { buildPreferenceScreen(context, sourceId) }
    // The registry loads APK sources after process start, so a restored sheet may find it empty.
    LaunchedEffect(screen) { if (screen == null) onDismissRequest() }
    if (screen == null) return

    // Preference objects are mutable Java objects, so changes are announced by bumping this.
    var revision by remember { mutableIntStateOf(0) }
    AppModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.source_extensions_settings_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Reading the revision here makes every group below redraw after a change.
            revision.let { PreferenceGroupContent(screen) { revision++ } }
        }
    }
}

private fun buildPreferenceScreen(context: Context, sourceId: SourceId): PreferenceScreen? {
    val settings = AnimeSourceRegistry.apkSourceSettings(sourceId) ?: return null
    return try {
        val manager = PreferenceManager(context)
        manager.sharedPreferencesName = "source_${settings.sourceId}"
        manager.createPreferenceScreen(context).also(settings.configurable::setupPreferenceScreen)
    } catch (error: Throwable) {
        // Extension code: a broken settings screen must not take the app down.
        AppLogger.w("ExtensionSettings", "Could not build settings for ${sourceId.value}", error)
        null
    }
}

/** The direct preferences of a group become one card list; every category becomes its own titled list. */
@Composable
private fun PreferenceGroupContent(group: PreferenceGroup, onChanged: () -> Unit) {
    val children = (0 until group.preferenceCount).map(group::getPreference).filter(Preference::isVisible)
    val loose = children.filter { it !is PreferenceGroup }
    if (loose.isNotEmpty()) {
        SettingsItems(count = loose.size) { index, shape ->
            PreferenceRow(loose[index], shape, onChanged)
        }
    }
    children.filterIsInstance<PreferenceGroup>().forEach { category ->
        val rows = (0 until category.preferenceCount).map(category::getPreference).filter(Preference::isVisible)
        if (rows.isEmpty()) return@forEach
        val name = category.title?.toString()
        if (name != null) {
            ExternalMetadataSheetGroup(title = name) {
                SettingsItems(count = rows.size) { index, shape -> PreferenceRow(rows[index], shape, onChanged) }
            }
        } else {
            SettingsItems(count = rows.size) { index, shape -> PreferenceRow(rows[index], shape, onChanged) }
        }
    }
}

@Composable
private fun PreferenceRow(
    preference: Preference,
    shape: androidx.compose.ui.graphics.Shape,
    onChanged: () -> Unit,
) {
    val title = preference.title?.toString().orEmpty()
    val summary = preference.summary?.toString()?.takeIf(String::isNotBlank)
    val enabled = preference.isEnabled
    when (preference) {
        is TwoStatePreference -> SettingsSwitchItem(
            icon = Icons.Outlined.ToggleOn,
            title = title,
            description = summary,
            checked = preference.isChecked,
            shape = shape,
            onCheckedChange = { next ->
                if (enabled && preference.callChangeListener(next)) {
                    preference.isChecked = next
                    onChanged()
                }
            },
        )

        is ListPreference -> {
            var open by remember { mutableStateOf(false) }
            PreferenceTextRow(Icons.Outlined.Tune, title, summary, enabled, shape) { open = true }
            if (open) {
                ChoiceDialog(
                    title = title,
                    entries = preference.entries.orEmpty().map(CharSequence::toString),
                    values = preference.entryValues.orEmpty().map(CharSequence::toString),
                    selected = setOfNotNull(preference.value),
                    multiple = false,
                    onDismiss = { open = false },
                    onConfirm = { chosen ->
                        open = false
                        chosen.firstOrNull()?.let { value ->
                            if (preference.callChangeListener(value)) {
                                preference.value = value
                                onChanged()
                            }
                        }
                    },
                )
            }
        }

        is MultiSelectListPreference -> {
            var open by remember { mutableStateOf(false) }
            PreferenceTextRow(Icons.Outlined.Checklist, title, summary, enabled, shape) { open = true }
            if (open) {
                ChoiceDialog(
                    title = title,
                    entries = preference.entries.orEmpty().map(CharSequence::toString),
                    values = preference.entryValues.orEmpty().map(CharSequence::toString),
                    selected = preference.values.orEmpty(),
                    multiple = true,
                    onDismiss = { open = false },
                    onConfirm = { chosen ->
                        open = false
                        if (preference.callChangeListener(chosen)) {
                            preference.values = chosen
                            onChanged()
                        }
                    },
                )
            }
        }

        is EditTextPreference -> {
            var open by remember { mutableStateOf(false) }
            PreferenceTextRow(Icons.Outlined.Edit, title, summary ?: preference.text, enabled, shape) { open = true }
            if (open) {
                TextDialog(
                    title = title,
                    initial = preference.text.orEmpty(),
                    onDismiss = { open = false },
                    onConfirm = { text ->
                        open = false
                        if (preference.callChangeListener(text)) {
                            preference.text = text
                            onChanged()
                        }
                    },
                )
            }
        }

        else -> PreferenceTextRow(Icons.Outlined.Tune, title, summary, enabled, shape) {
            preference.onPreferenceClickListener?.onPreferenceClick(preference)
            onChanged()
        }
    }
}

@Composable
private fun PreferenceTextRow(
    icon: ImageVector,
    title: String,
    summary: String?,
    enabled: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
) {
    SettingsItemRow(icon = icon, shape = shape, onClick = { if (enabled) onClick() }) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val alpha = if (enabled) 1f else 0.5f
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f * alpha),
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
        }
    }
}

/** The dialog shell shared by every extension preference: a rounded surface sized to its content. */
@Composable
private fun PreferenceDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                content()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    Button(onClick = onConfirm) { Text(confirmLabel) }
                }
            }
        }
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    entries: List<String>,
    values: List<String>,
    selected: Set<String>,
    multiple: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var chosen by remember { mutableStateOf(selected) }
    val haptic = LocalHapticFeedback.current
    PreferenceDialog(
        title = title,
        onDismiss = onDismiss,
        confirmLabel = stringResource(android.R.string.ok),
        onConfirm = { onConfirm(chosen) },
    ) {
        val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.55f).dp
        Column(modifier = Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())) {
            SettingsItems(count = values.size) { index, shape ->
                val value = values[index]
                val checked = value in chosen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(
                            if (checked) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            if (multiple) {
                                chosen = if (checked) chosen - value else chosen + value
                            } else {
                                chosen = setOf(value)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = entries.getOrElse(index) { value },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    if (checked) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TextDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    PreferenceDialog(
        title = title,
        onDismiss = onDismiss,
        confirmLabel = stringResource(android.R.string.ok),
        onConfirm = { onConfirm(text) },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )
    }
}

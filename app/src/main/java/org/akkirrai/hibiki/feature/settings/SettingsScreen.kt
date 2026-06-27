package org.akkirrai.hibiki.feature.settings

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.app.settings.AppPreferencesState
import org.akkirrai.hibiki.app.settings.LanguageMode
import org.akkirrai.hibiki.app.settings.ThemeMode
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.AppTonalSurface
import org.akkirrai.hibiki.core.log.AppLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = UiDimens.ScreenPadding,
    appPreferences: AppPreferences? = null
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

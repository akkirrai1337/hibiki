package org.akkirrai.hibiki.feature.settings

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.LanguageMode
import org.akkirrai.hibiki.app.settings.LocalAppPreferences
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.app.settings.ThemeMode
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.log.PerfLogger

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 24.dp,
    onCheckForUpdates: () -> Unit = {},
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val haptic = LocalHapticFeedback.current
    val appPreferences = LocalAppPreferences.current
    val preferences = LocalAppPreferencesState.current
    val versionName = remember(context) {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }

    LaunchedEffect(Unit) {
        PerfLogger.mark("SettingsScreen composed")
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 18.dp,
            top = 24.dp,
            end = 18.dp,
            bottom = bottomContentPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item(key = "appearance") {
            SettingsSection(title = stringResource(R.string.settings_appearance)) {
                SettingsItems(count = 3) { index, shape ->
                    when (index) {
                        0 -> SettingsVerticalItem(
                            icon = Icons.Outlined.DarkMode,
                            title = stringResource(R.string.settings_theme),
                            shape = shape,
                        ) {
                            SettingsSegmentedControl(
                                options = listOf(ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.SYSTEM),
                                selectedOption = preferences.themeMode,
                                label = ::themeModeLabel,
                                onSelect = { mode ->
                                    appPreferences.setThemeMode(mode)
                                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                },
                            )
                        }

                        1 -> SettingsSwitchItem(
                            icon = Icons.Outlined.Palette,
                            title = stringResource(R.string.settings_use_system_color_scheme),
                            checked = preferences.useSystemColorScheme,
                            shape = shape,
                            onCheckedChange = appPreferences::setUseSystemColorScheme,
                        )

                        2 -> SettingsSwitchItem(
                            icon = Icons.Outlined.Contrast,
                            title = stringResource(R.string.settings_amoled),
                            checked = preferences.useAmoledTheme,
                            shape = shape,
                            onCheckedChange = appPreferences::setUseAmoledTheme,
                        )
                    }
                }
            }
        }

        item(key = "preferences") {
            SettingsSection(title = stringResource(R.string.settings_preferences)) {
                SettingsItems(count = 1) { _, shape ->
                    SettingsVerticalItem(
                        icon = Icons.Outlined.Language,
                        title = stringResource(R.string.settings_language),
                        shape = shape,
                    ) {
                        SettingsSegmentedControl(
                            options = listOf(LanguageMode.RUSSIAN, LanguageMode.ENGLISH, LanguageMode.SYSTEM),
                            selectedOption = preferences.languageMode,
                            label = ::languageModeLabel,
                            onSelect = { mode ->
                                appPreferences.setLanguageMode(mode)
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            },
                        )
                    }
                }
            }
        }

        item(key = "player") {
            SettingsSection(title = stringResource(R.string.settings_player)) {
                SettingsItems(count = 1) { _, _ ->
                    SettingsSwitchItem(
                        icon = Icons.Outlined.SkipNext,
                        title = stringResource(R.string.settings_auto_skip_segments),
                        checked = preferences.autoSkipSegments,
                        shape = CircleShape,
                        onCheckedChange = appPreferences::setAutoSkipSegments,
                    )
                }
            }
        }

        item(key = "updates") {
            SettingsSection(title = stringResource(R.string.settings_updates)) {
                SettingsItems(count = 1) { _, _ ->
                    SettingsActionItem(
                        icon = Icons.Outlined.Update,
                        title = stringResource(R.string.settings_check_updates),
                        shape = CircleShape,
                        onClick = onCheckForUpdates,
                    )
                }
            }
        }

        item(key = "support") {
            SettingsSection(title = stringResource(R.string.settings_support)) {
                SettingsItems(count = 1) { _, _ ->
                    SettingsActionItem(
                        icon = Icons.Outlined.Share,
                        title = stringResource(R.string.settings_export_logs),
                        shape = CircleShape,
                        onClick = {
                            AppLogger.shareLogs(context).onFailure {
                                Toast.makeText(
                                    context,
                                    R.string.settings_export_logs_failed,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                    )
                }
            }
        }

        item(key = "about") {
            SettingsSection(title = stringResource(R.string.settings_about)) {
                SettingsAboutItem(
                    versionName = versionName,
                    onGitHubClick = { uriHandler.openUri(HIBIKI_GITHUB_URL) },
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        content()
    }
}

@Composable
private fun SettingsItems(
    count: Int,
    content: @Composable ColumnScope.(index: Int, shape: Shape) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(count) { index ->
            content(index, settingsItemShape(index, count))
        }
    }
}

private fun settingsItemShape(index: Int, count: Int): Shape {
    if (count == 1) return RoundedCornerShape(24.dp)
    return RoundedCornerShape(
        topStart = if (index == 0) 24.dp else 4.dp,
        topEnd = if (index == 0) 24.dp else 4.dp,
        bottomStart = if (index == count - 1) 24.dp else 4.dp,
        bottomEnd = if (index == count - 1) 24.dp else 4.dp,
    )
}

@Composable
private fun SettingsVerticalItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    shape: Shape,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsItemHeader(icon = icon, title = title)
        content()
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    checked: Boolean,
    shape: Shape,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable {
                onCheckedChange(!checked)
                haptic.performHapticFeedback(
                    if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                )
            }
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
        )
        SettingsSwitch(
            checked = checked,
            onCheckedChange = {
                onCheckedChange(it)
                haptic.performHapticFeedback(
                    if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                )
            },
        )
    }
}

@Composable
private fun SettingsActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    shape: Shape,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
        )
    }
}

@Composable
private fun SettingsItemHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
        )
    }
}

@Composable
private fun SettingsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        thumbContent = if (checked) {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun <T> SettingsSegmentedControl(
    options: List<T>,
    selectedOption: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = option == selectedOption
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(
                        if (selected) {
                            CircleShape
                        } else {
                            segmentShape(index, options.lastIndex)
                        },
                    )
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.background
                        },
                    )
                    .clickable { onSelect(option) }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun segmentShape(index: Int, lastIndex: Int): Shape {
    return when (index) {
        0 -> RoundedCornerShape(
            topStart = 16.dp,
            bottomStart = 16.dp,
            topEnd = 6.dp,
            bottomEnd = 6.dp,
        )

        lastIndex -> RoundedCornerShape(
            topStart = 6.dp,
            bottomStart = 6.dp,
            topEnd = 16.dp,
            bottomEnd = 16.dp,
        )

        else -> RoundedCornerShape(6.dp)
    }
}

@Composable
private fun SettingsAboutItem(
    versionName: String,
    onGitHubClick: () -> Unit,
) {
    val context = LocalContext.current
    val appIcon = remember(context) {
        context.packageManager
            .getApplicationIcon(context.packageName)
            .toBitmap(config = Bitmap.Config.ARGB_8888)
            .asImageBitmap()
    }
    val isDarkMode = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val background = if (isDarkMode) Color(0x190FFF66) else Color(0x59FFC0CB)
    val textColor = if (isDarkMode) Color(0xFF3BFF84) else Color(0xFFDA6482)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        Image(
            bitmap = appIcon,
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
            )
            Text(
                text = "v$versionName",
                style = MaterialTheme.typography.labelMedium,
                color = textColor.copy(alpha = 0.75f),
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClick = onGitHubClick),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_github),
                contentDescription = stringResource(R.string.settings_github),
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun languageModeLabel(mode: LanguageMode): String {
    return stringResource(
        when (mode) {
            LanguageMode.SYSTEM -> R.string.settings_language_system
            LanguageMode.RUSSIAN -> R.string.settings_language_russian
            LanguageMode.ENGLISH -> R.string.settings_language_english
        },
    )
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String {
    return stringResource(
        when (mode) {
            ThemeMode.SYSTEM -> R.string.settings_theme_system
            ThemeMode.LIGHT -> R.string.settings_theme_light
            ThemeMode.DARK -> R.string.settings_theme_dark
        },
    )
}

private const val HIBIKI_GITHUB_URL = "https://github.com/akkirrai1337/hibiki"

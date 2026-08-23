package org.akkirrai.hibiki.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

@Composable
fun AppSettingsVerticalItem(
    headerContent: @Composable () -> Unit,
    shape: Shape,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, shape)
            .padding(SettingsItemPadding),
        verticalArrangement = Arrangement.spacedBy(SettingsItemContentGap),
    ) {
        headerContent()
        content()
    }
}

@Composable
fun AppSettingsItemRow(
    iconContent: @Composable () -> Unit,
    shape: Shape,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainer, shape)
            .padding(SettingsItemPadding),
        horizontalArrangement = Arrangement.spacedBy(SettingsItemContentGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        iconContent()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SettingsItemTextGap),
            content = content,
        )
        trailing?.invoke()
    }
}

@Composable
fun AppSettingsItemHeader(
    iconContent: @Composable () -> Unit,
    title: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(SettingsItemContentGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        iconContent()
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
        )
    }
}

@Composable
fun AppSettingsActionItem(
    iconContent: @Composable () -> Unit,
    title: String,
    subtitle: String? = null,
    shape: Shape,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    AppSettingsItemRow(
        iconContent = iconContent,
        shape = shape,
        onClick = onClick,
        trailing = trailing,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AppSettingsSwitchItem(
    iconContent: @Composable () -> Unit,
    title: String,
    checked: Boolean,
    shape: Shape,
    onCheckedChange: (Boolean) -> Unit,
) {
    AppSettingsItemRow(
        iconContent = iconContent,
        shape = shape,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            AppSettingsSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
        )
    }
}

@Composable
fun AppSettingsToggleItem(
    iconContent: @Composable () -> Unit,
    title: String,
    checked: Boolean,
    shape: Shape,
    onClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
) {
    AppSettingsItemRow(
        iconContent = iconContent,
        shape = shape,
        onClick = onClick,
        trailing = {
            AppSettingsSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
        )
    }
}

@Composable
fun AppSettingsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        thumbContent = if (checked) {
            {
                androidx.compose.material3.Text(
                    text = "✓",
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            null
        },
    )
}

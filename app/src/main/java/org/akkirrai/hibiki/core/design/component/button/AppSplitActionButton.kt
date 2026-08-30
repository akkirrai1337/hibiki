package org.akkirrai.hibiki.core.design.component.button

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

data class AppSplitActionMenuItem(
    val label: String,
    val onClick: () -> Unit,
)

@Composable
fun AppSplitActionButton(
    primaryLabel: String,
    secondaryLabel: String,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secondaryActions: List<AppSplitActionMenuItem> = listOf(
        AppSplitActionMenuItem(label = secondaryLabel, onClick = onSecondaryClick),
    ),
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val containerColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SplitActionSurface(
            modifier = Modifier.height(36.dp),
            shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp, topEnd = 6.dp, bottomEnd = 6.dp),
            containerColor = containerColor,
            contentColor = contentColor,
            enabled = enabled,
            onClick = onPrimaryClick,
        ) {
            Text(
                text = primaryLabel,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Box(modifier = Modifier.width(36.dp).height(36.dp), contentAlignment = Alignment.TopStart) {
            SplitActionSurface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 18.dp, bottomEnd = 18.dp),
                containerColor = containerColor,
                contentColor = contentColor,
                enabled = enabled,
                onClick = { menuExpanded = true },
            ) {
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = secondaryLabel)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                secondaryActions.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action.label) },
                        onClick = {
                            menuExpanded = false
                            action.onClick()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SplitActionSurface(
    modifier: Modifier,
    shape: RoundedCornerShape,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) { content() }
    }
}

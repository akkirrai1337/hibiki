package org.akkirrai.hibiki.shared.design.component.button

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppSplitActionButton(
    primaryLabel: String,
    secondaryLabel: String,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
    arrowWidth: Dp = 36.dp,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val containerColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    }
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(
            shape = splitActionPrimaryShape(height),
            color = containerColor,
            contentColor = contentColor,
            modifier = Modifier
                .weight(1f)
                .height(height)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onPrimaryClick,
                ),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = primaryLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.8.sp),
                )
            }
        }
        Box(
            modifier = Modifier.width(arrowWidth).height(height),
            contentAlignment = Alignment.TopStart,
        ) {
            Surface(
                shape = splitActionSecondaryShape(height),
                color = containerColor,
                contentColor = contentColor,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClick = { menuExpanded = true },
                    ),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = secondaryLabel,
                        modifier = Modifier.width(22.dp).height(22.dp),
                    )
                }
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(secondaryLabel) },
                    onClick = {
                        menuExpanded = false
                        onSecondaryClick()
                    },
                )
            }
        }
    }
}

private fun splitActionPrimaryShape(height: Dp) = RoundedCornerShape(
    topStart = height / 2,
    bottomStart = height / 2,
    topEnd = 6.dp,
    bottomEnd = 6.dp,
)

private fun splitActionSecondaryShape(height: Dp) = RoundedCornerShape(
    topStart = 6.dp,
    bottomStart = 6.dp,
    topEnd = height / 2,
    bottomEnd = height / 2,
)

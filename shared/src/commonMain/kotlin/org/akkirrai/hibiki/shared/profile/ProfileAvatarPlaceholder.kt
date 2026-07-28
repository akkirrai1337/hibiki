package org.akkirrai.hibiki.shared.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ProfileAvatarPlaceholder(
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = Icons.Outlined.Person,
        contentDescription = null,
        modifier = modifier,
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
    )
}

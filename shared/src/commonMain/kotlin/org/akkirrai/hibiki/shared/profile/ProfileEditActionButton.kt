package org.akkirrai.hibiki.shared.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ProfileEditActionButton(
    isEditing: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ProfileActionButton(
        icon = if (isEditing) Icons.Rounded.Check else Icons.Rounded.Edit,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
    )
}

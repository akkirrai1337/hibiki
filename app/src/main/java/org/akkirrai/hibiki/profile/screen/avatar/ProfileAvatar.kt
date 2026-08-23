package org.akkirrai.hibiki.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.akkirrai.hibiki.design.animation.continuousRotation

@Composable
fun ProfileAvatar(
    ratio: Float,
    isEditing: Boolean,
    editContentDescription: String,
    onEditClick: () -> Unit,
    avatarContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrimAlpha by animateFloatAsState(if (isEditing) 0.38f else 0f, tween(300), label = "avatar_scrim")
    Box(modifier.size(ProfileAvatarContainerSize).graphicsLayer { alpha = (1.5f * ratio - 0.5f).coerceIn(0f, 1f) }, contentAlignment = Alignment.Center) {
        Surface(Modifier.fillMaxSize(), shape = CircleShape) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer))), contentAlignment = Alignment.Center) {
                avatarContent(Modifier.size(ProfileAvatarContentSize))
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrimAlpha)))
            }
        }
        if (isEditing) {
            Surface(Modifier.align(Alignment.TopEnd).size(ProfileAvatarEditButtonSize).clickable(onClick = onEditClick), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface) {
                Icon(Icons.Rounded.Edit, editContentDescription, Modifier.padding(ProfileAvatarEditIconPadding))
            }
        }
    }
}

@Composable
fun ProfileAvatarImage(
    url: String,
) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
}

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

@Composable
fun ProfileSettingsActionButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ProfileActionButton(
        icon = Icons.Rounded.Settings,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        iconModifier = Modifier.continuousRotation(
            durationMillis = 10_000,
            label = "settings_icon_rotation",
        ),
    )
}

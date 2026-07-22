package org.akkirrai.hibiki.shared.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun ProfileAvatar(
    ratio: Float,
    isEditing: Boolean,
    editIcon: ImageVector,
    editContentDescription: String,
    onEditClick: () -> Unit,
    avatarContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrimAlpha by animateFloatAsState(if (isEditing) 0.38f else 0f, tween(300), label = "avatar_scrim")
    Box(modifier.size(70.dp).graphicsLayer { alpha = (1.5f * ratio - 0.5f).coerceIn(0f, 1f) }, contentAlignment = Alignment.Center) {
        Surface(Modifier.fillMaxSize(), shape = CircleShape) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer))), contentAlignment = Alignment.Center) {
                avatarContent()
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrimAlpha)))
            }
        }
        if (isEditing) {
            Surface(Modifier.align(Alignment.TopEnd).size(32.dp).clickable(onClick = onEditClick), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface) {
                Icon(editIcon, editContentDescription, Modifier.padding(7.dp))
            }
        }
    }
}

package org.akkirrai.hibiki.design.component.floating

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object AppFloatingHeaderDefaults {
    val ControlHeight: Dp = 48.dp
    val ControlRadius: Dp = 24.dp
    val ControlIconSize: Dp = 22.dp
    val TitleHorizontalPadding: Dp = 18.dp

    @Composable
    fun containerColor(): Color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f)
}

@Composable
fun AppFloatingIconButton(
    imageVector: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun AppFloatingPill(
    modifier: Modifier = Modifier,
    containerColor: Color,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
fun AppFloatingTitlePill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color,
) {
    AppFloatingPill(modifier = modifier, containerColor = containerColor) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 18.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

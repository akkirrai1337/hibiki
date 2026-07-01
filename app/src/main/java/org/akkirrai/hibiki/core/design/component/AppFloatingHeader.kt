package org.akkirrai.hibiki.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.design.UiDimens

object AppFloatingHeaderDefaults {
    @Composable
    fun containerColor(): Color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f)

    @Composable
    fun scrimBrush(): Brush {
        val surface = MaterialTheme.colorScheme.surface
        return Brush.verticalGradient(
            colorStops = arrayOf(
                0f to surface.copy(alpha = 0.96f),
                0.62f to surface.copy(alpha = 0.58f),
                1f to surface.copy(alpha = 0f),
            ),
        )
    }
}

@Composable
fun AppFloatingHeader(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    includeStatusBarsPadding: Boolean = true,
    containerColor: Color = AppFloatingHeaderDefaults.containerColor(),
    scrimBrush: Brush = AppFloatingHeaderDefaults.scrimBrush(),
    actions: (@Composable () -> Unit)? = null,
) {
    val baseModifier = if (includeStatusBarsPadding) {
        modifier.statusBarsPadding()
    } else {
        modifier
    }
    Box(
        modifier = baseModifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .background(scrimBrush),
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(
                    start = UiDimens.ScreenPadding,
                    top = 14.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppFloatingBackButton(
                onClick = onBackClick,
                containerColor = containerColor,
            )
            AppFloatingTitlePill(
                text = title,
                containerColor = containerColor,
            )
        }
        if (actions != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = 14.dp,
                        end = UiDimens.ScreenPadding,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                actions()
            }
        }
    }
}

@Composable
fun AppFloatingBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = AppFloatingHeaderDefaults.containerColor(),
) {
    AppFloatingIconButton(
        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
        contentDescription = stringResource(R.string.cd_back),
        onClick = onClick,
        modifier = modifier,
        containerColor = containerColor,
    )
}

@Composable
fun AppFloatingIconButton(
    imageVector: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = AppFloatingHeaderDefaults.containerColor(),
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
fun AppFloatingTitlePill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = AppFloatingHeaderDefaults.containerColor(),
) {
    AppFloatingPill(
        modifier = modifier,
        containerColor = containerColor,
    ) {
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

@Composable
fun AppFloatingPill(
    modifier: Modifier = Modifier,
    containerColor: Color = AppFloatingHeaderDefaults.containerColor(),
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

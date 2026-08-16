package org.akkirrai.hibiki.shared.catalog.sort

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private const val CatalogSortOrderAnimationDurationMillis = 300

@Composable
fun AppCatalogSortOrderIcon(
    atEnd: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    val rotation by animateFloatAsState(
        targetValue = if (atEnd) 180f else 0f,
        animationSpec = tween(
            durationMillis = CatalogSortOrderAnimationDurationMillis,
            easing = FastOutSlowInEasing,
        ),
        label = "catalogSortOrderRotation",
    )
    Icon(
        imageVector = remember { catalogSortOrderImageVector() },
        contentDescription = null,
        modifier = modifier.graphicsLayer { rotationZ = rotation },
        tint = tint,
    )
}

private fun catalogSortOrderImageVector(): ImageVector = ImageVector.Builder(
    name = "CatalogSortOrder",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 960f,
    viewportHeight = 960f,
).path(fill = SolidColor(Color.Black)) {
    moveTo(480f, 672f)
    lineTo(324f, 827f)
    quadTo(313f, 838f, 296.5f, 838.5f)
    quadTo(280f, 839f, 268f, 827f)
    quadTo(257f, 816f, 257f, 799f)
    quadTo(257f, 782f, 268f, 771f)
    lineTo(423f, 616f)
    quadTo(446f, 593f, 480f, 593f)
    quadTo(514f, 593f, 537f, 616f)
    lineTo(692f, 771f)
    quadTo(703f, 782f, 703.5f, 798.5f)
    quadTo(704f, 815f, 692f, 827f)
    quadTo(681f, 838f, 664f, 838f)
    quadTo(647f, 838f, 636f, 827f)
    close()

    moveTo(480f, 432f)
    lineTo(324f, 587f)
    quadTo(313f, 598f, 296.5f, 598.5f)
    quadTo(280f, 599f, 268f, 587f)
    quadTo(257f, 576f, 257f, 559f)
    quadTo(257f, 542f, 268f, 531f)
    lineTo(423f, 376f)
    quadTo(446f, 353f, 480f, 353f)
    quadTo(514f, 353f, 537f, 376f)
    lineTo(692f, 531f)
    quadTo(703f, 542f, 703.5f, 558.5f)
    quadTo(704f, 575f, 692f, 587f)
    quadTo(681f, 598f, 664f, 598f)
    quadTo(647f, 598f, 636f, 587f)
    close()

    moveTo(480f, 192f)
    lineTo(324f, 347f)
    quadTo(313f, 358f, 296.5f, 358.5f)
    quadTo(280f, 359f, 268f, 347f)
    quadTo(257f, 336f, 257f, 319f)
    quadTo(257f, 302f, 268f, 291f)
    lineTo(423f, 136f)
    quadTo(446f, 113f, 480f, 113f)
    quadTo(514f, 113f, 537f, 136f)
    lineTo(692f, 291f)
    quadTo(703f, 302f, 703.5f, 318.5f)
    quadTo(704f, 335f, 692f, 347f)
    quadTo(681f, 358f, 664f, 358f)
    quadTo(647f, 358f, 636f, 347f)
    close()
}.build()

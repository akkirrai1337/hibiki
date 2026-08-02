package org.akkirrai.hibiki.shared.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.layout.LocalAppLayoutEnvironment

object AppFloatingHeaderDefaults {
    val ControlHeight: Dp = 48.dp
    val ControlRadius: Dp = 24.dp
    val ControlIconSize: Dp = 22.dp
    val TitleHorizontalPadding: Dp = 18.dp

    @Composable
    fun containerColor(): Color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f)
}

@Composable
fun AppFloatingHeader(
    title: String,
    onBackClick: () -> Unit,
    backContentDescription: String?,
    modifier: Modifier = Modifier,
    includeStatusBarsPadding: Boolean = true,
    containerColor: Color = AppFloatingHeaderDefaults.containerColor(),
    actions: (@Composable () -> Unit)? = null,
) {
    val layoutEnvironment = LocalAppLayoutEnvironment.current
    val baseModifier = if (includeStatusBarsPadding) {
        modifier.then(
            if (layoutEnvironment.isProvided) {
                Modifier.padding(top = layoutEnvironment.topSystemInset)
            } else {
                Modifier.statusBarsPadding()
            },
        )
    } else {
        modifier
    }
    Box(
        modifier = baseModifier.fillMaxWidth(),
    ) {
        AppTopScrim()
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
            AppFloatingIconButton(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = backContentDescription,
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

package org.akkirrai.hibiki.core.design.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.shared.design.component.AppFloatingHeader as SharedFloatingHeader
import org.akkirrai.hibiki.shared.design.component.AppFloatingHeaderDefaults as SharedFloatingHeaderDefaults
import org.akkirrai.hibiki.shared.design.component.AppFloatingIconButton as SharedFloatingIconButton
import org.akkirrai.hibiki.shared.design.component.AppFloatingPill as SharedFloatingPill
import org.akkirrai.hibiki.shared.design.component.AppFloatingTitlePill as SharedFloatingTitlePill

typealias AppFloatingHeaderDefaults = SharedFloatingHeaderDefaults

@Composable
fun AppFloatingHeader(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    includeStatusBarsPadding: Boolean = true,
    containerColor: Color = AppFloatingHeaderDefaults.containerColor(),
    actions: (@Composable () -> Unit)? = null,
) = SharedFloatingHeader(
    title = title,
    onBackClick = onBackClick,
    backIcon = Icons.AutoMirrored.Outlined.ArrowBack,
    backContentDescription = stringResource(R.string.cd_back),
    modifier = modifier,
    includeStatusBarsPadding = includeStatusBarsPadding,
    containerColor = containerColor,
    actions = actions,
)

@Composable
fun AppFloatingBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = AppFloatingHeaderDefaults.containerColor(),
) = SharedFloatingIconButton(
    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
    contentDescription = stringResource(R.string.cd_back),
    onClick = onClick,
    modifier = modifier,
    containerColor = containerColor,
)

@Composable
fun AppFloatingIconButton(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = AppFloatingHeaderDefaults.containerColor(),
) = SharedFloatingIconButton(
    imageVector = imageVector,
    contentDescription = contentDescription,
    onClick = onClick,
    modifier = modifier,
    containerColor = containerColor,
)

@Composable
fun AppFloatingTitlePill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = AppFloatingHeaderDefaults.containerColor(),
) = SharedFloatingTitlePill(
    text = text,
    modifier = modifier,
    containerColor = containerColor,
)

@Composable
fun AppFloatingPill(
    modifier: Modifier = Modifier,
    containerColor: Color = AppFloatingHeaderDefaults.containerColor(),
    content: @Composable () -> Unit,
) = SharedFloatingPill(
    modifier = modifier,
    containerColor = containerColor,
    content = content,
)

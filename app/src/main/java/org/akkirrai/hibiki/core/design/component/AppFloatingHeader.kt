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

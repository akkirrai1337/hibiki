package org.akkirrai.hibiki.core.design.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.akkirrai.hibiki.R

@Composable
fun AppBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: AppBackButtonStyle = AppBackButtonStyle.Surface,
) {
    AppFilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        style = style.toFilledStyle(),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = stringResource(R.string.cd_back)
        )
    }
}

enum class AppBackButtonStyle {
    Surface,
    DarkOverlay,
    HeroOverlay,
}

private fun AppBackButtonStyle.toFilledStyle(): AppFilledIconButtonStyle = when (this) {
    AppBackButtonStyle.Surface -> AppFilledIconButtonStyle.Surface
    AppBackButtonStyle.DarkOverlay -> AppFilledIconButtonStyle.DarkOverlay
    AppBackButtonStyle.HeroOverlay -> AppFilledIconButtonStyle.HeroOverlay
}

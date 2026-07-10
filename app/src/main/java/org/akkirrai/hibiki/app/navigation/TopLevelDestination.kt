package org.akkirrai.hibiki.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import org.akkirrai.hibiki.R

enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    Home("home", R.string.nav_home, Icons.Outlined.Home),
    Library("library", R.string.nav_library, Icons.Outlined.VideoLibrary),
    Settings("settings", R.string.nav_settings, Icons.Outlined.Settings)
}

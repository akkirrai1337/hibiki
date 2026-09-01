package org.akkirrai.hibiki.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import org.akkirrai.hibiki.R

enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Home("home", R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    Catalog("catalog", R.string.nav_catalog, Icons.Outlined.Explore, Icons.Filled.Explore),
    Library("library", R.string.nav_library, Icons.Outlined.VideoLibrary, Icons.Filled.VideoLibrary),
    Sources("sources", R.string.nav_sources, Icons.Outlined.Extension, Icons.Filled.Extension),
    Profile("profile", R.string.nav_profile, Icons.Outlined.Person, Icons.Filled.Person),
}

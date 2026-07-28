package org.akkirrai.hibiki.app.navigation

import androidx.annotation.StringRes
import org.akkirrai.hibiki.R

enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
) {
    Home("home", R.string.nav_home),
    Catalog("catalog", R.string.nav_catalog),
    Library("library", R.string.nav_library),
    Sources("sources", R.string.nav_sources),
    Profile("profile", R.string.nav_profile)
}

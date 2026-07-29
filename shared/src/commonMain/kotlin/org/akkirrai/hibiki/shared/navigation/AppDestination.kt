package org.akkirrai.hibiki.shared.navigation

import org.akkirrai.hibiki.shared.text.AppTextKey

enum class AppDestination(val textKey: AppTextKey) {
    HOME(AppTextKey.Home),
    CATALOG(AppTextKey.Catalog),
    LIBRARY(AppTextKey.Library),
    SOURCES(AppTextKey.Sources),
    PROFILE(AppTextKey.Profile),
    SETTINGS(AppTextKey.Settings),
}

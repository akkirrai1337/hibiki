package org.akkirrai.hibiki.shared.navigation

import org.akkirrai.hibiki.shared.text.AppTextKey

enum class AppDestination(val textKey: AppTextKey) {
    HOME(AppTextKey.Home),
    SEARCH(AppTextKey.Search),
    LIBRARY(AppTextKey.Library),
    SETTINGS(AppTextKey.Settings),
}

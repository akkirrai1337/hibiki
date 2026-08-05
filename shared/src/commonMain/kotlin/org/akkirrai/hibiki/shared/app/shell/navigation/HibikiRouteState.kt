package org.akkirrai.hibiki.shared.app.shell.navigation

import org.akkirrai.hibiki.shared.navigation.AppDestination
import org.akkirrai.hibiki.shared.navigation.AppRoute

internal fun shouldApplyTopSystemInset(destination: AppDestination): Boolean =
    destination != AppDestination.SETTINGS

internal fun AppRoute?.downloadModeEnabled(): Boolean = when (this) {
    is AppRoute.WatchSources -> downloadMode
    is AppRoute.Episodes -> downloadMode
    else -> false
}

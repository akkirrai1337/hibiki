package org.akkirrai.hibiki.app.navigation

/**
 * NavController's push/replace vocabulary, modeled as a plain operation so the decision logic
 * below is unit-testable without a real `NavController` (which needs Robolectric or an
 * instrumented test to construct). The NavHost wiring milestone translates these into
 * `navController.navigate(route) { popUpTo(currentTop) { inclusive = true } }` calls for
 * [Replace], and a plain `navController.navigate(route)` for [Push].
 */
internal sealed interface AndroidBackStackOp {
    data class Push(val route: AndroidNavigationRoute) : AndroidBackStackOp
    data class Replace(val route: AndroidNavigationRoute) : AndroidBackStackOp
}

/**
 * Applies an [AndroidBackStackOp] to a plain backstack list, mirroring what
 * `navController.navigate(...)` and `popUpTo(currentTop) { inclusive = true }` do to the real
 * backstack. Exists so the decision functions below can be verified against exact backstack
 * contents the same way the legacy [WatchFlowNavigationTest]-style tests do.
 */
internal fun List<AndroidNavigationRoute>.applyBackStackOp(
    op: AndroidBackStackOp,
): List<AndroidNavigationRoute> = when (op) {
    is AndroidBackStackOp.Push -> this + op.route
    is AndroidBackStackOp.Replace -> dropLast(1) + op.route
}

/**
 * Decides whether moving from the Watch Sources list to a chosen Episodes destination should
 * push a new backstack entry or replace the current one.
 *
 * Mirrors the legacy `replaceWatchSourcesWithEpisodes` vs. `navigateToEpisodes` split in
 * `WatchFlowReducer.kt`: a title with exactly one voiceover skips the intermediate Watch
 * Sources screen entirely, so Back from Episodes lands on Details directly instead of
 * bouncing through a source list the user never meaningfully chose from.
 */
internal fun watchSourcesToEpisodesOp(
    availableSourceCount: Int,
    route: AndroidNavigationRoute.Episodes,
): AndroidBackStackOp = if (availableSourceCount == 1) {
    AndroidBackStackOp.Replace(route)
} else {
    AndroidBackStackOp.Push(route)
}

/**
 * Decides whether selecting an episode should push a new Player entry or replace the current
 * one.
 *
 * Mirrors the legacy `navigateToPlayer` in `WatchFlowReducer.kt`: switching episodes while
 * already on Player keeps the backstack depth constant (an in-place episode swap); opening the
 * player for the first time from Episodes pushes a new entry.
 */
internal fun playerNavigationOp(
    currentTop: AndroidNavigationRoute?,
    route: AndroidNavigationRoute.Player,
): AndroidBackStackOp = if (currentTop is AndroidNavigationRoute.Player) {
    AndroidBackStackOp.Replace(route)
} else {
    AndroidBackStackOp.Push(route)
}

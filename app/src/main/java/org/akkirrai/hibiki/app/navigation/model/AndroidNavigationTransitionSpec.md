# Transition-identity spec for the NavHost migration

Reference for Milestone 2 (wiring `NavHost` in). Captures the *intent* behind the legacy
`appShellTransitionKey`/`AppTransitionKey` folding (see `AppShellTransitionKey.kt` and the
three-branch key computation in `HibikiAppShell.kt`) in terms of `AndroidNavigationRoute`, so
the actual `NavHost` `enterTransition`/`exitTransition`/`popEnterTransition`/`popExitTransition`
lambdas can be written against it later without re-deriving the intent from scratch. Not code
yet -- the real lambdas depend on how the graph ends up nested (single graph vs. a nested
watch-flow sub-graph), which Milestone 2 decides.

## Rule 1 -- tab content never re-animates for Details/Settings on top

Today: `TopLevel`, `Details`, and `Settings` routes share one shell-level transition identity
per tab (`"$topLevelDestination:$selectedTab"`, with `Settings` folded to `PROFILE:PROFILE`).
Pushing/popping `Details` or `Settings` must not cross-fade the tab content sitting underneath.

NavHost equivalent: `Details` and `Settings` should be destinations that render *on top of* the
tab graph (their own `AnimatedContent`/dialog-style transition), not siblings inside the same
`NavHost` whose default cross-fade would apply to the tab switch itself. Concretely, keep them
out of the bottom-tab `NavHost`'s route set entirely, the same way `HibikiAppDestinationContent.kt`
handles them today as separate `AnimatedContent` blocks layered outside `AppDestinationTopLevelRoutes`.

## Rule 2 -- the watch flow is one continuous slot per anime id

Today: `WatchSources`, `Episodes`, and `Player` share one transition identity keyed **only** on
`animeId` (`isWatchFlowRoute()` + the "watch-flow" branch in `HibikiAppShell.kt`). Picking a
voiceover (WatchSources -> Episodes) or an episode (Episodes -> Player) recomposes the existing
slot in place with no cross-fade; only opening the watch flow for a *different* anime fades.

NavHost equivalent: give `WatchSources`/`Episodes`/`Player` `enterTransition`/`exitTransition`
of `EnterTransition.None`/`ExitTransition.None` when the previous and next destination's
`animeId` are equal, and the normal fade only when entering the flow fresh (previous destination
is not part of the watch flow, or belongs to a different anime).

## Rule 3 -- everything else fades normally

`SourceRepositories`/`SourcePackageInfo` (today's fallback branch, using the full
`AppRoute.transitionKey()`) and ordinary tab-to-tab switches get the standard
`fadeIn`/`fadeOut` (`AppMotion.ScreenTransitionDurationMillis`, see `appScreenTransition` in
`AppProductionRoot.kt`) with no special-casing.

## Direction (`Forward` vs `Pop`)

Today `AppNavigationState.transitionDirection` is set per-reducer-event (`Forward` on
`Navigate`/`SelectTopLevel`/`Replace`, `Pop` on `Back`/`DismissOverlay`) but `appScreenTransition`
does not currently vary the animation shape by direction -- both directions fade the same way.
NavHost's `popEnterTransition`/`popExitTransition` can therefore mirror `enterTransition`/
`exitTransition` exactly (no separate pop-specific spec needed) unless a future visual pass
wants push/pop to look different, which is out of scope for this migration.

package org.akkirrai.hibiki.shared.navigation

/** Stable identity used by the shared root transition for top-level and nested content. */
fun appShellTransitionKey(
    topLevelDestination: AppTopLevelDestination,
    selectedTab: String,
    detailsId: String?,
    watchId: String?,
    sourceId: String?,
    routeKey: AppTransitionKey,
): AppTransitionKey = AppTransitionKey(
    route = "app-shell",
    identity = buildString {
        append(topLevelDestination.name)
        append(':')
        append(selectedTab)
        append(':')
        append(detailsId.orEmpty())
        append(':')
        append(watchId.orEmpty())
        append(':')
        append(sourceId.orEmpty())
        append(':')
        append(routeKey.route)
        append(':')
        append(routeKey.identity)
    },
)

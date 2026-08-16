package org.akkirrai.hibiki.shared.layout

/** Layout policy supplied by a platform host to the shared app shell. */
class AppLayoutOptions(
    val showSettingsBackButton: Boolean = true,
    val includeNavigationBarPadding: Boolean = true,
    val applyStatusBarPadding: Boolean = false,
)

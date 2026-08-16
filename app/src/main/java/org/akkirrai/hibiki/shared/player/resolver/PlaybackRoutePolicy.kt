package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.navigation.AppRoute

/** Shows the platform playback surface only while the common shell is on Player. */
fun shouldShowPlaybackHost(
    currentRoute: AppRoute,
    hasPlayback: Boolean,
    hasPendingContext: Boolean,
): Boolean = currentRoute is AppRoute.Player && (hasPlayback || hasPendingContext)

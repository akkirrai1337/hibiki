package org.akkirrai.hibiki.shared.app

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber

internal const val IosPlayerOrientationNotification =
    "org.akkirrai.hibiki.player.orientation"

/** Delegates rotation to the UIKit scene owner without relying on private orientation APIs. */
internal fun setIosPlayerLandscape(active: Boolean) {
    NSNotificationCenter.defaultCenter.postNotificationName(
        aName = IosPlayerOrientationNotification,
        `object` = NSNumber.numberWithBool(active),
    )
}

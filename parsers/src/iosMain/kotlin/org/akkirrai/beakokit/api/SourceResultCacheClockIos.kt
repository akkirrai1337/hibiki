package org.akkirrai.beakokit.api

import platform.Foundation.NSDate

internal actual fun currentWallClockMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1_000).toLong()

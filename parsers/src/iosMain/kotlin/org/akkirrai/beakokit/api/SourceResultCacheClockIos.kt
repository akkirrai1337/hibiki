package org.akkirrai.beakokit.api

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

internal actual fun currentWallClockMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1_000).toLong()

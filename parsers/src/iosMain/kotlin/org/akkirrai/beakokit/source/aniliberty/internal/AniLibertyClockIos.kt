package org.akkirrai.beakokit.source.aniliberty.internal

import platform.Foundation.NSDate

internal actual fun currentTimeMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1_000).toLong()

package org.akkirrai.beakokit.api

import platform.Foundation.NSProcessInfo

internal actual fun monotonicTimeMillis(): Long =
    (NSProcessInfo.processInfo.systemUptime * 1_000).toLong()

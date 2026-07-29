package org.akkirrai.hibiki.shared.platform

import platform.CoreFoundation.CFAbsoluteTimeGetCurrent

actual fun currentEpochSeconds(): Long = (CFAbsoluteTimeGetCurrent() + 978_307_200.0).toLong()

package org.akkirrai.beakokit.http

import java.io.IOException

internal actual fun isRetryableNetworkException(cause: Throwable): Boolean = cause is IOException

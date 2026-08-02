package org.akkirrai.beakokit.http

internal expect fun isRetryableNetworkException(cause: Throwable): Boolean

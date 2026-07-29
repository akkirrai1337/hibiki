package org.akkirrai.beakokit.http

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import kotlinx.io.IOException

internal actual fun isRetryableNetworkException(cause: Throwable): Boolean =
    cause is IOException || cause is ConnectTimeoutException || cause is SocketTimeoutException

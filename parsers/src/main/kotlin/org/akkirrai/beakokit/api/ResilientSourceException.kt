package org.akkirrai.beakokit.api

import java.io.IOException

internal fun isPlatformTransientSourceFailure(cause: Throwable): Boolean = cause is IOException

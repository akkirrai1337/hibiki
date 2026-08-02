package org.akkirrai.beakokit.api

class SourceRepositoryStateException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

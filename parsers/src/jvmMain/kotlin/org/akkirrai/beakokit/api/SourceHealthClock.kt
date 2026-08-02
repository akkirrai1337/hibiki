package org.akkirrai.beakokit.api

@PublishedApi
internal actual fun monotonicTimeMillis(): Long = System.nanoTime() / 1_000_000

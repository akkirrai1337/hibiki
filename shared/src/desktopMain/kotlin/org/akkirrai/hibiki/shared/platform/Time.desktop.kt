package org.akkirrai.hibiki.shared.platform

actual fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1_000L

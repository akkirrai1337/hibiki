package org.akkirrai.hibiki.shared.player

fun formatPlaybackSpeed(speed: Float): String = if (speed == 1f) "1x" else "${speed}x"

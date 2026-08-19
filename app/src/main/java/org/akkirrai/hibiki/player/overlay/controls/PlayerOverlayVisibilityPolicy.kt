package org.akkirrai.hibiki.player

fun shouldShowSkipSegmentPrompt(
    controlsVisible: Boolean,
    playerLocked: Boolean,
    playlistVisible: Boolean,
    settingsVisible: Boolean,
): Boolean = controlsVisible && !playerLocked && !playlistVisible && !settingsVisible

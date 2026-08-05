package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.player.model.PlaybackLinkOption

fun uniquePlayerNames(links: List<PlaybackLinkOption>): List<String> = links
    .mapNotNull { it.playerName }
    .distinct()

package org.akkirrai.hibiki.core.source

import org.akkirrai.beakokit.model.AnimeReleaseStatus
import org.akkirrai.hibiki.shared.source.resolveReleaseStatusLabel

fun AnimeReleaseStatus.localizedDisplayName(preferEnglish: Boolean): String =
    resolveReleaseStatusLabel(name, preferEnglish)

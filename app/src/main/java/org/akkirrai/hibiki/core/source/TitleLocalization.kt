package org.akkirrai.hibiki.core.source

import android.content.Context
import org.akkirrai.beakokit.model.AnimeReleaseStatus
import org.akkirrai.hibiki.core.model.ReleaseStatusText

/** The status name from string resources, in the app language. */
fun AnimeReleaseStatus.localizedDisplayName(context: Context): String = ReleaseStatusText.label(context, this)

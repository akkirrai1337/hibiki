package org.akkirrai.hibiki.core.source

import android.content.Context
import java.io.IOException
import org.akkirrai.beakokit.api.SourceErrorKind
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.hibiki.R

/** Keeps site/internal error text out of search results while preserving an actionable reason. */
fun Throwable.toSearchErrorMessage(context: Context): String {
    val sourceError = this as? SourceException
    return when (sourceError?.kind) {
        SourceErrorKind.NETWORK,
        SourceErrorKind.UNAVAILABLE -> context.getString(R.string.error_search_unavailable)
        SourceErrorKind.AUTH -> context.getString(R.string.error_search_access)
        SourceErrorKind.RATE_LIMITED -> context.getString(R.string.error_search_rate_limited)
        SourceErrorKind.PARSE -> context.getString(R.string.error_search_invalid_response)
        SourceErrorKind.NOT_FOUND,
        SourceErrorKind.UNKNOWN,
        null -> if (this is IOException) {
            context.getString(R.string.error_search_unavailable)
        } else {
            context.getString(R.string.error_search_failed)
        }
    }
}

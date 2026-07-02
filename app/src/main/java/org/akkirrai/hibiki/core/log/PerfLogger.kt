package org.akkirrai.hibiki.core.log

import android.os.SystemClock

object PerfLogger {
    private const val TAG = "HibikiPerf"

    fun mark(event: String, details: String = "") {
        AppLogger.d(TAG, format(event, details))
    }

    fun <T> measure(event: String, details: String = "", block: () -> T): T {
        val startedAt = SystemClock.elapsedRealtime()
        return try {
            block()
        } finally {
            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
            mark(event, appendDuration(details, elapsedMs))
        }
    }

    fun elapsedMs(startedAt: Long): Long {
        return SystemClock.elapsedRealtime() - startedAt
    }

    private fun format(event: String, details: String): String {
        return if (details.isBlank()) event else "$event: $details"
    }

    private fun appendDuration(details: String, elapsedMs: Long): String {
        val duration = "duration=${elapsedMs}ms"
        return if (details.isBlank()) duration else "$details, $duration"
    }
}

package org.akkirrai.hibiki.shared.update

import kotlin.math.round

fun formatDownloadSize(bytes: Long): String {
    val tenthsOfMib = round(bytes.toDouble() / (1024.0 * 1024.0) * 10.0).toLong()
    return "${tenthsOfMib / 10}.${kotlin.math.abs(tenthsOfMib % 10)} MB"
}

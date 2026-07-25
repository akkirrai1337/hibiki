package org.akkirrai.hibiki.shared.details

fun formatCompactCount(value: Long): String = when {
    value >= 1_000_000L -> formatCompactUnit(value, 1_000_000L, "M")
    value >= 1_000L -> formatCompactUnit(value, 1_000L, "K")
    else -> value.toString()
}

private fun formatCompactUnit(value: Long, unit: Long, suffix: String): String {
    val tenths = (value * 10L + unit / 2L) / unit
    return "${tenths / 10L}.${tenths % 10L}$suffix"
}

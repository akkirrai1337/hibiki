package org.akkirrai.hibiki.shared.profile

import kotlin.time.Clock

private const val PROFILE_ACTIVITY_HISTORY_DAYS = 30
private const val EPOCH_DAY_OFFSET = 719468L

fun defaultProfileActivityDateStrings(): List<String> {
    val today = Clock.System.now().epochSeconds / 86_400L
    return (0 until PROFILE_ACTIVITY_HISTORY_DAYS).map { offset ->
        epochDayToIso(today - (PROFILE_ACTIVITY_HISTORY_DAYS - 1 - offset))
    }
}

fun profileActivityDateLabel(date: String): String {
    val parts = date.split('-')
    return if (parts.size == 3) "${parts[2]}.${parts[1]}" else date
}

fun profileAddedDateLabel(value: Long): String {
    val epochMillis = if (value in 1 until 1_000_000_000_000L) value * 1_000L else value
    val epochDay = epochMillis / 86_400_000L
    val isoDate = epochDayToIso(epochDay)
    val parts = isoDate.split('-')
    return if (parts.size == 3) "${parts[2]}.${parts[1]}.${parts[0]}" else isoDate
}

private fun epochDayToIso(epochDay: Long): String {
    val shiftedDay = epochDay + EPOCH_DAY_OFFSET
    val era = if (shiftedDay >= 0) shiftedDay / 146_097L else (shiftedDay - 146_096L) / 146_097L
    val dayOfEra = shiftedDay - era * 146_097L
    val yearOfEra = (dayOfEra - dayOfEra / 1_460L + dayOfEra / 36_524L - dayOfEra / 146_096L) / 365L
    var year = yearOfEra + era * 400L
    val dayOfYear = dayOfEra - (365L * yearOfEra + yearOfEra / 4L - yearOfEra / 100L)
    val monthPart = (5L * dayOfYear + 2L) / 153L
    val day = dayOfYear - (153L * monthPart + 2L) / 5L + 1L
    val month = monthPart + if (monthPart < 10L) 3L else -9L
    year += if (month <= 2L) 1L else 0L
    return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}

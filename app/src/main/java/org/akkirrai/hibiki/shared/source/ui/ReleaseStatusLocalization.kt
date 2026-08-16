package org.akkirrai.hibiki.shared.source

fun resolveReleaseStatusLabel(statusKey: String, preferEnglish: Boolean): String = when {
    statusKey.equals("ongoing", ignoreCase = true) ->
        if (preferEnglish) "Ongoing" else "Онгоинг"
    statusKey.equals("released", ignoreCase = true) ->
        if (preferEnglish) "Released" else "Вышел"
    statusKey.equals("announcement", ignoreCase = true) ->
        if (preferEnglish) "Announcement" else "Анонс"
    else -> if (preferEnglish) "Unknown" else "Неизвестно"
}

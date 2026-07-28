package org.akkirrai.hibiki.shared.library

fun libraryStatusAlias(value: String): String {
    val normalized = value.trim().lowercase()
    return when {
        normalized.contains("ongoing") || normalized.contains("releasing") || normalized.contains("airing") || normalized.contains("онгоинг") -> "ongoing"
        normalized.contains("released") || normalized.contains("finished") || normalized.contains("completed") || normalized.contains("вышел") || normalized.contains("заверш") -> "released"
        normalized.contains("announced") || normalized.contains("not_yet") || normalized.contains("анонс") -> "announced"
        normalized.contains("cancel") || normalized.contains("отмен") -> "cancelled"
        normalized.contains("hiatus") || normalized.contains("перерыв") -> "hiatus"
        else -> normalized
    }
}

fun libraryStatusLabel(value: String, isRussian: Boolean): String = when (libraryStatusAlias(value)) {
    "ongoing" -> if (isRussian) "Онгоинг" else "Ongoing"
    "released" -> if (isRussian) "Вышел" else "Released"
    "announced" -> if (isRussian) "Анонс" else "Announced"
    "cancelled" -> if (isRussian) "Отменено" else "Cancelled"
    "hiatus" -> if (isRussian) "Перерыв" else "Hiatus"
    else -> value
}

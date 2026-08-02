package org.akkirrai.hibiki.shared.source

fun localizeYummyStatusFilterLabel(id: String, title: String): String = when (id) {
    "released" -> "Вышел"
    "ongoing" -> "Онгоинг"
    "announcement" -> "Анонс"
    else -> title
}

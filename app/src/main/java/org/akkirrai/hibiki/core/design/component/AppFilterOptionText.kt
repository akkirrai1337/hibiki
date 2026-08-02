package org.akkirrai.hibiki.core.design.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/** Applies the app language to known source aliases without changing their filter values. */
@Composable
fun appFilterOptionText(value: String): String {
    if (LocalConfiguration.current.locales[0]?.language != "ru") return value
    return russianFilterOptionLabels[value.trim().lowercase()] ?: value
}

private val russianFilterOptionLabels = mapOf(
    "ongoing" to "Онгоинг", "releasing" to "Онгоинг", "airing" to "Онгоинг",
    "finished" to "Завершено", "completed" to "Завершено", "released" to "Вышло",
    "announced" to "Анонс", "not yet released" to "Анонс", "not_yet_released" to "Анонс",
    "cancelled" to "Отменено", "canceled" to "Отменено", "hiatus" to "Перерыв", "paused" to "Перерыв",
    "movie" to "Фильм", "film" to "Фильм", "special" to "Спецвыпуск", "music" to "Музыка",
    "action" to "Экшен", "adventure" to "Приключения", "comedy" to "Комедия", "drama" to "Драма",
    "fantasy" to "Фэнтези", "horror" to "Ужасы", "mystery" to "Мистика", "romance" to "Романтика",
    "sci-fi" to "Научная фантастика", "science fiction" to "Научная фантастика", "slice of life" to "Повседневность",
    "sports" to "Спорт", "supernatural" to "Сверхъестественное", "thriller" to "Триллер", "psychological" to "Психология",
    "mecha" to "Меха", "school" to "Школа", "historical" to "Историческое", "military" to "Военное",
    "magic" to "Магия", "martial arts" to "Боевые искусства", "detective" to "Детектив", "isekai" to "Исекай",
    "seinen" to "Сэйнэн", "shounen" to "Сёнэн", "shoujo" to "Сёдзё", "josei" to "Дзёсэй",
    "kids" to "Детское", "parody" to "Пародия", "vampire" to "Вампиры", "demons" to "Демоны",
    "game" to "Игры", "harem" to "Гарем", "reverse harem" to "Обратный гарем", "ecchi" to "Этти",
)

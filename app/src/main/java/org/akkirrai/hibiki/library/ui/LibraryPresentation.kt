package org.akkirrai.hibiki.library.ui

import org.akkirrai.hibiki.app.settings.LanguageMode
import org.akkirrai.hibiki.app.settings.resolveAppLanguageTag
import org.akkirrai.hibiki.library.LibraryCategory

fun LibraryCategory.localizationKey(): String = when (this) {
    LibraryCategory.Watching -> "library_category_watching"
    LibraryCategory.Planned -> "library_category_planned"
    LibraryCategory.Completed -> "library_category_completed"
    LibraryCategory.Dropped -> "library_category_dropped"
    LibraryCategory.OnHold -> "library_category_on_hold"
    LibraryCategory.Favorite -> "library_category_favorite"
    LibraryCategory.Saved -> "library_category_saved"
    LibraryCategory.Recent -> "library_category_recent"
}

data class LibraryEmptyStateText(
    val title: String,
    val message: String,
)

fun resolveLibraryEmptyStateText(
    filtered: Boolean,
    searchQuery: String,
    category: LibraryCategory,
    emptyTitle: String,
    emptyMessage: String,
    filteredTitle: String,
    searchTitle: String,
    filteredMessage: String,
    categoryLabels: Map<LibraryCategory, String>,
): LibraryEmptyStateText {
    if (!filtered) return LibraryEmptyStateText(emptyTitle, emptyMessage)

    return LibraryEmptyStateText(
        title = if (searchQuery.isBlank()) filteredTitle else searchTitle,
        message = if (searchQuery.isBlank()) {
            resolveLibraryEmptyStateMessage(category, categoryLabels)
        } else {
            filteredMessage
        },
    )
}

fun isRussianLibraryLanguage(
    languageMode: LanguageMode,
    systemLanguage: String,
): Boolean = resolveAppLanguageTag(languageMode, systemLanguage) == "ru"

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

fun resolveLibraryEmptyStateMessage(
    category: LibraryCategory,
    labels: Map<LibraryCategory, String>,
): String = labels.getValue(category)

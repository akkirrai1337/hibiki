package org.akkirrai.hibiki.feature.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.core.download.OfflineDownloadRepository
import org.akkirrai.hibiki.core.source.AnimeSearchRepository
import org.akkirrai.hibiki.core.source.LibraryCategory
import org.akkirrai.hibiki.core.source.LibraryEntry
import org.akkirrai.hibiki.core.source.LibraryRepository
import org.akkirrai.hibiki.core.source.OfflineTitleMetadataRepository

class LibraryViewModel(
    context: Context,
    private val libraryRepository: LibraryRepository = LibraryRepository(context.applicationContext),
    private val searchRepository: AnimeSearchRepository = AnimeSearchRepository(context.applicationContext),
    private val offlineDownloadRepository: OfflineDownloadRepository = OfflineDownloadRepository(context.applicationContext),
    private val offlineTitleMetadataRepository: OfflineTitleMetadataRepository = OfflineTitleMetadataRepository(context.applicationContext),
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        syncFromStorage()
        refreshDetails()
    }

    fun syncFromStorage() {
        val saved = libraryRepository.getLibraryEntries()
        _uiState.update {
            it.copy(
                entries = saved,
                selectedCategory = preferredCategory(saved, it.selectedCategory),
            )
        }
        reconcileSavedDownloadsAsync()
    }

    private fun reconcileSavedDownloadsAsync() {
        viewModelScope.launch(Dispatchers.IO) {
            val changed = reconcileSavedDownloads()
            if (changed) {
                val saved = libraryRepository.getLibraryEntries()
                _uiState.update {
                    it.copy(
                        entries = saved,
                        selectedCategory = preferredCategory(saved, it.selectedCategory),
                    )
                }
            }
        }
    }

    private fun reconcileSavedDownloads(): Boolean {
        var changed = false
        offlineDownloadRepository.getOfflineTitleIds().forEach { titleId ->
            val metadata = offlineTitleMetadataRepository.get(titleId) ?: return@forEach
            if (LibraryCategory.Saved !in libraryRepository.getLibraryCategories(titleId)) {
                libraryRepository.saveToLibrary(metadata, LibraryCategory.Saved)
                changed = true
            }
        }
        return changed
    }

    private fun refreshDetails() {
        val saved = _uiState.value.entries
        if (saved.isEmpty()) {
            return
        }

        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val refreshed = saved
                .groupBy { entry -> entry.anime.id }
                .flatMap { (_, groupedEntries) ->
                    val baseEntry = groupedEntries.first()
                    val currentCategories = libraryRepository.getLibraryCategories(baseEntry.anime.id)
                    if (currentCategories.isEmpty()) {
                        return@flatMap emptyList()
                    }

                    val freshAnime = runCatching { searchRepository.getDetails(baseEntry.anime.id, baseEntry.anime) }
                        .getOrNull()
                    val anime = freshAnime ?: baseEntry.anime
                    if (freshAnime != null) {
                        currentCategories.sortedBy(LibraryCategory::ordinal).forEach { category ->
                            libraryRepository.saveToLibrary(freshAnime, category)
                        }
                    }
                    currentCategories
                        .sortedBy(LibraryCategory::ordinal)
                        .map { category -> baseEntry.copy(anime = anime, category = category) }
                }
            _uiState.update {
                it.copy(
                    entries = refreshed,
                    selectedCategory = preferredCategory(refreshed, it.selectedCategory),
                    isRefreshing = false,
                )
            }
        }
    }

    fun selectCategory(category: LibraryCategory) {
        val saved = libraryRepository.getLibraryEntries()
        _uiState.update {
            it.copy(
                entries = saved,
                selectedCategory = category,
            )
        }
        reconcileSavedDownloadsAsync()
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "") }
    }

    fun applySearchFilters(filters: LibrarySearchFilters) {
        _uiState.update { it.copy(searchFilters = filters) }
    }

    fun resetSearchFilters() {
        _uiState.update { it.copy(searchFilters = LibrarySearchFilters()) }
    }

    private fun preferredCategory(
        entries: List<LibraryEntry>,
        current: LibraryCategory,
    ): LibraryCategory {
        if (entries.any { it.category == current }) return current
        return orderedLibraryCategories(entries)
            .firstOrNull { category -> entries.any { it.category == category } }
            ?: current
    }

    override fun onCleared() {
        searchRepository.close()
        super.onCleared()
    }

    class Factory(
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LibraryViewModel(context = context) as T
        }
    }
}

data class LibraryUiState(
    val entries: List<LibraryEntry> = emptyList(),
    val selectedCategory: LibraryCategory = LibraryCategory.Watching,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val searchFilters: LibrarySearchFilters = LibrarySearchFilters(),
) {
    val orderedCategories: List<LibraryCategory>
        get() = orderedLibraryCategories(entries)

    val visibleEntries: List<LibraryEntry>
        get() {
            val normalizedQuery = searchQuery.trim()
            return entries.filter { entry ->
                entry.category == selectedCategory &&
                    searchFilters.matches(entry) &&
                    (
                        normalizedQuery.isBlank() ||
                            entry.anime.title.contains(normalizedQuery, ignoreCase = true) ||
                            entry.anime.subtitle.contains(normalizedQuery, ignoreCase = true)
                        )
            }
        }

    val categoryCounts: Map<LibraryCategory, Int>
        get() = entries.groupingBy { it.category }.eachCount()

    val filterCatalog: LibraryFilterCatalog
        get() {
            val categoryEntries = entries.filter { it.category == selectedCategory }
            return LibraryFilterCatalog(
                typeOptions = categoryEntries.mapNotNull { it.anime.extractLibraryType() }.distinct().sorted(),
                statusOptions = categoryEntries.map { it.anime.status.trim() }.filter(String::isNotBlank).distinct().sorted(),
                genreOptions = categoryEntries.flatMap { it.anime.genres }.map(String::trim).filter(String::isNotBlank).distinct().sorted(),
            )
        }
}

data class LibrarySearchFilters(
    val type: String? = null,
    val status: String? = null,
    val includedGenres: Set<String> = emptySet(),
    val excludedGenres: Set<String> = emptySet(),
) {
    fun matches(entry: LibraryEntry): Boolean {
        val anime = entry.anime
        val typeMatches = type == null || anime.extractLibraryType() == type
        val statusMatches = status == null || anime.status.equals(status, ignoreCase = true)
        val animeGenres = anime.genres.map(String::trim).filter(String::isNotBlank).toSet()
        val includesMatch = includedGenres.isEmpty() || includedGenres.all { it in animeGenres }
        val excludesMatch = excludedGenres.none { it in animeGenres }
        return typeMatches && statusMatches && includesMatch && excludesMatch
    }

    fun hasActiveFilters(): Boolean {
        return type != null || status != null || includedGenres.isNotEmpty() || excludedGenres.isNotEmpty()
    }
}

data class LibraryFilterCatalog(
    val typeOptions: List<String> = emptyList(),
    val statusOptions: List<String> = emptyList(),
    val genreOptions: List<String> = emptyList(),
)

private fun org.akkirrai.hibiki.core.model.Anime.extractLibraryType(): String? {
    return subtitle
        .split(Regex("\\s*[•·|]\\s*"))
        .map(String::trim)
        .firstOrNull { value ->
            value.isNotBlank() && value.any(Char::isLetter) && value.none(Char::isDigit)
        }
}

private fun orderedLibraryCategories(entries: List<LibraryEntry>): List<LibraryCategory> {
    return if (entries.any { it.category == LibraryCategory.Saved }) {
        listOf(LibraryCategory.Saved) + LibraryCategory.entries.filter { it != LibraryCategory.Saved }
    } else {
        LibraryCategory.entries.toList()
    }
}

package org.akkirrai.hibiki.core.profile

import org.akkirrai.animeresolver.metadata.YummyUserAnimeListItem
import org.akkirrai.hibiki.core.source.LibraryCategory
import org.akkirrai.hibiki.core.source.YummyIdMigration

internal sealed interface LibraryMergeAction {
    data class ImportRemote(
        val id: String,
        val remote: YummyUserAnimeListItem,
        val categories: Set<LibraryCategory>,
    ) : LibraryMergeAction

    data class RecordConflict(
        val id: String,
        val remote: YummyUserAnimeListItem,
        val localCategory: LibraryCategory,
        val remoteCategory: LibraryCategory,
    ) : LibraryMergeAction

    data class ClearConflict(val id: String) : LibraryMergeAction
    data class AddRemotePrimary(
        val id: String,
        val category: LibraryCategory,
    ) : LibraryMergeAction
    data class AddRemoteFavorite(val id: String) : LibraryMergeAction
}

internal fun planLibraryMerge(
    localItems: List<LocalLibraryItem>,
    remoteItems: List<YummyUserAnimeListItem>,
): List<LibraryMergeAction> {
    val localById = localItems.associateBy(LocalLibraryItem::id)
    return buildList {
        remoteItems.distinctBy(YummyUserAnimeListItem::animeId).forEach { remote ->
            val id = YummyIdMigration.normalizeTitleId(remote.animeId.toString())
            val local = localById[id]
            val remoteCategory = remote.list?.toLibraryCategory()

            if (local == null) {
                val categories = buildSet {
                    remoteCategory?.let(::add)
                    if (remote.isFavorite) add(LibraryCategory.Favorite)
                }
                if (categories.isNotEmpty()) {
                    add(LibraryMergeAction.ImportRemote(id, remote, categories))
                }
                return@forEach
            }

            val localCategory = local.categories.primaryStatusCategory()
            if (localCategory == null && remoteCategory != null) {
                add(LibraryMergeAction.AddRemotePrimary(id, remoteCategory))
                add(LibraryMergeAction.ClearConflict(id))
            } else if (localCategory != null && remoteCategory != null && localCategory != remoteCategory) {
                add(LibraryMergeAction.RecordConflict(id, remote, localCategory, remoteCategory))
            } else {
                add(LibraryMergeAction.ClearConflict(id))
            }

            if (remote.isFavorite && LibraryCategory.Favorite !in local.categories) {
                add(LibraryMergeAction.AddRemoteFavorite(id))
            }
        }
    }
}

internal fun Set<LibraryCategory>.primaryStatusCategory(): LibraryCategory? =
    firstOrNull { it != LibraryCategory.Favorite && it != LibraryCategory.Saved }

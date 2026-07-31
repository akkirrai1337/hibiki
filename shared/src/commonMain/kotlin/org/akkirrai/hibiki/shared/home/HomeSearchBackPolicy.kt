package org.akkirrai.hibiki.shared.home

enum class HomeSearchBackAction {
    DismissIme,
    ClearSearch,
    None,
}

fun homeSearchBackAction(
    isImeVisible: Boolean,
    isSearchActive: Boolean,
): HomeSearchBackAction = when {
    isImeVisible -> HomeSearchBackAction.DismissIme
    isSearchActive -> HomeSearchBackAction.ClearSearch
    else -> HomeSearchBackAction.None
}

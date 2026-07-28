package org.akkirrai.hibiki.shared.source

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class SourceLanguageSectionContent<T>(
    val key: String,
    val title: String,
    val items: List<T>,
)

fun <T> LazyListScope.appSourceLanguageSections(
    sections: List<SourceLanguageSectionContent<T>>,
    trailingContent: @Composable (Modifier) -> Unit,
    emptyContent: @Composable () -> Unit,
    isSelected: (T) -> Boolean,
    itemContent: @Composable (item: T, selected: Boolean, modifier: Modifier) -> Unit,
) {
    sections.forEach { section ->
        if (section.items.isEmpty()) return@forEach
        item(key = "${section.key}_sources") {
            AppSourceLanguageContent(
                stateKey = section.key,
                title = section.title,
                items = section.items,
                trailingContent = trailingContent,
                emptyContent = emptyContent,
                isSelected = isSelected,
                itemContent = itemContent,
            )
        }
    }
}

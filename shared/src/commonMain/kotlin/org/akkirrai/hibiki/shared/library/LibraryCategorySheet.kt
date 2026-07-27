package org.akkirrai.hibiki.shared.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog

@Composable
fun AppLibraryCategorySheet(
    selectedCategory: LibraryCategory?,
    title: String,
    subtitle: String,
    savedNote: String,
    removeAction: String,
    categoryLabels: Map<LibraryCategory, String>,
    onCategoryClick: (LibraryCategory) -> Unit,
    onRemoveClick: () -> Unit,
    onDismiss: () -> Unit,
    iconContent: @Composable (LibraryCategory, Modifier) -> Unit,
    selectedIconContent: @Composable (Modifier) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = LibraryCategorySheetMaxWidth),
            shape = RoundedCornerShape(LibraryCategorySheetCornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = LibraryCategorySheetElevation,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = LibraryCategorySheetMaxHeight)
                    .padding(LibraryCategorySheetContentPadding),
                verticalArrangement = Arrangement.spacedBy(LibraryCategorySheetSectionGap),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(LibraryCategorySheetHeaderGap)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(top = LibraryCategorySheetDividerTopPadding),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f),
                        )
                    }
                }

                items(
                    items = LibraryCategory.entries.filter { it != LibraryCategory.Saved },
                    key = LibraryCategory::name,
                ) { category ->
                    AppLibraryCategorySheetItem(
                        label = categoryLabels.getValue(category),
                        selected = category == selectedCategory,
                        onClick = { onCategoryClick(category) },
                        iconContent = { iconModifier -> iconContent(category, iconModifier) },
                        selectedIconContent = selectedIconContent,
                    )
                }

                if (selectedCategory == LibraryCategory.Saved) {
                    item {
                        Text(
                            text = savedNote,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = LibraryCategorySheetSavedNoteTopPadding),
                        )
                    }
                } else if (selectedCategory != null) {
                    item {
                        TextButton(
                            onClick = onRemoveClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = LibraryCategorySheetRemoveTopPadding),
                            shape = RoundedCornerShape(LibraryCategorySheetRemoveCornerRadius),
                        ) {
                            Text(removeAction)
                        }
                    }
                }
            }
        }
    }
}

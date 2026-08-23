package org.akkirrai.hibiki.library.screen
import org.akkirrai.hibiki.library.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
                    items = LibraryCategory.entries.filter {
                        it != LibraryCategory.Saved && it != LibraryCategory.Recent
                    },
                    key = LibraryCategory::name,
                ) { category ->
                    AppLibraryCategorySheetItem(
                        label = categoryLabels.getValue(category),
                        selected = category == selectedCategory,
                        onClick = { onCategoryClick(category) },
                        iconContent = { iconModifier ->
                            Icon(
                                imageVector = category.icon(),
                                contentDescription = null,
                                modifier = iconModifier,
                                tint = if (category == selectedCategory) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                        selectedIconContent = { iconModifier ->
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                modifier = iconModifier,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
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

@Composable
private fun AppLibraryCategorySheetItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    iconContent: @Composable (Modifier) -> Unit,
    selectedIconContent: @Composable (Modifier) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(LibraryCategorySheetItemCornerRadius),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.46f)
        },
        border = BorderStroke(
            width = LibraryCategorySheetItemBorderWidth,
            color = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f)
            },
        ),
        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = LibraryCategorySheetItemMinHeight)
                .padding(horizontal = LibraryCategorySheetItemHorizontalPadding, vertical = LibraryCategorySheetItemVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(LibraryCategorySheetItemContentGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            iconContent(Modifier.size(LibraryCategorySheetItemIconSize))
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selected) {
                selectedIconContent(Modifier.size(LibraryCategorySheetItemSelectedIconSize))
            }
        }
    }
}

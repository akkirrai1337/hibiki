package org.akkirrai.hibiki.shared.search

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.UiDimens

@Composable
fun AppSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    placeholder: String,
    searchContentDescription: String?,
    searchIcon: ImageVector,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().height(58.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingIcon = { Icon(searchIcon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingIcon = {
            IconButton(onClick = onSearch) {
                Icon(searchIcon, searchContentDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        shape = RoundedCornerShape(UiDimens.MediumCorner),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledIndicatorColor = MaterialTheme.colorScheme.surfaceContainer,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

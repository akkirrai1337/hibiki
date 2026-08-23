package org.akkirrai.hibiki.details.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.akkirrai.hibiki.layout.appBottomSystemInsetPadding

@Composable
fun AppDetailsTitleSheetContent(
    title: String,
    description: String,
) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .fillMaxSize(),
    ) {
        Text(
            text = title,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = DetailsTitleSheetHeaderHorizontalPadding, vertical = DetailsTitleSheetHeaderVerticalPadding),
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 16.sp,
                lineHeight = 20.sp,
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DetailsTitleSheetDescriptionHorizontalPadding, vertical = DetailsTitleSheetDescriptionVerticalPadding)
                .appBottomSystemInsetPadding(),
        )
    }
}

@Composable
fun AppDetailsTitleSheetDragHandle(
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = if (expanded) Icons.Rounded.Close else Icons.Rounded.KeyboardArrowUp,
        contentDescription = null,
        modifier = modifier
            .padding(8.dp)
            .size(if (expanded) 16.dp else 20.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

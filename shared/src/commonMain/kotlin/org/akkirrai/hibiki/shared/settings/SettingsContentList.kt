package org.akkirrai.hibiki.shared.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
fun AppSettingsContentList(
    bottomContentPadding: Dp,
    topContentPadding: Dp = SettingsContentTopPadding,
    content: LazyListScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SettingsContentHorizontalPadding,
            top = topContentPadding,
            end = SettingsContentHorizontalPadding,
            bottom = bottomContentPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(SettingsContentSectionGap),
        content = content,
    )
}

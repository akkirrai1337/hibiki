package org.akkirrai.hibiki.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun AppSettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(SettingsSectionContentGap)) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        content()
    }
}

@Composable
fun AppSettingsItems(
    count: Int,
    content: @Composable ColumnScope.(index: Int, shape: Shape) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SettingsItemGap)) {
        repeat(count) { index -> content(index, appSettingsItemShape(index, count)) }
    }
}

private fun appSettingsItemShape(index: Int, count: Int): Shape {
    if (count == 1) return RoundedCornerShape(SettingsItemOuterCornerRadius)
    return RoundedCornerShape(
        topStart = if (index == 0) SettingsItemOuterCornerRadius else SettingsItemInnerCornerRadius,
        topEnd = if (index == 0) SettingsItemOuterCornerRadius else SettingsItemInnerCornerRadius,
        bottomStart = if (index == count - 1) SettingsItemOuterCornerRadius else SettingsItemInnerCornerRadius,
        bottomEnd = if (index == count - 1) SettingsItemOuterCornerRadius else SettingsItemInnerCornerRadius,
    )
}

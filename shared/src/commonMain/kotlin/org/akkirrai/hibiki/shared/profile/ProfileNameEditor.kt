package org.akkirrai.hibiki.shared.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ProfileNameEditor(
    label: String,
    name: String,
    onNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleLarge,
) {
    val underlineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
    Column(modifier = modifier.widthIn(min = 150.dp, max = 240.dp)) {
        Text(label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
        BasicTextField(
            value = name,
            onValueChange = onNameChange,
            singleLine = true,
            textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp).drawBehind {
                drawLine(underlineColor, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            },
        )
    }
}

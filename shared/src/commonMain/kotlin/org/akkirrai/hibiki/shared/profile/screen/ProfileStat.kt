package org.akkirrai.hibiki.shared.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign

@Composable
fun ProfileStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        Text(value, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
    }
}

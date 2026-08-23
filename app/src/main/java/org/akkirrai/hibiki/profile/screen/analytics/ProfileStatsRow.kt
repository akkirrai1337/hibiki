package org.akkirrai.hibiki.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign

data class ProfileStatItem(val label: String, val value: String)

@Composable
fun ProfileStatsRow(items: List<ProfileStatItem>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        items.forEach { item -> ProfileStat(item.label, item.value) }
    }
}

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

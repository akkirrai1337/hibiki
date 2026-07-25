package org.akkirrai.hibiki.shared.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

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

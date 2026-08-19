package org.akkirrai.hibiki.profile

import androidx.compose.ui.graphics.Color

data class RecentLibraryItem(
    val title: String,
    val posterUrl: String?,
    val ratingLabel: String?,
    val statusLabel: String,
    val dateLabel: String,
    val color: Color,
)

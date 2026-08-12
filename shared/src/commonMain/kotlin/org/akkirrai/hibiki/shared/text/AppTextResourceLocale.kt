package org.akkirrai.hibiki.shared.text

import androidx.compose.runtime.Composable

@Composable
expect fun AppTextResourceLocale(
    languageTag: String,
    content: @Composable () -> Unit,
)

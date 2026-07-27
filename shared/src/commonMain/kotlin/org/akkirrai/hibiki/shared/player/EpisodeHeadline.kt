package org.akkirrai.hibiki.shared.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

@Composable
fun buildEpisodeHeadline(
    headline: String,
    trailingLabel: String?,
): AnnotatedString {
    if (trailingLabel.isNullOrBlank()) return AnnotatedString(headline)

    return buildAnnotatedString {
        append(headline)
        withStyle(
            SpanStyle(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Normal,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
            ),
        ) {
            append(" • $trailingLabel")
        }
    }
}

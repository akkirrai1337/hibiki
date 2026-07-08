package org.akkirrai.hibiki.core.design.component

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun AnimeTitleText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    minLines: Int = 1,
    baseMaxLines: Int = 2,
    extraLongTitleLines: Int = 2,
    overflow: TextOverflow = TextOverflow.Clip,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        color = color,
        textAlign = textAlign,
        minLines = minLines,
        maxLines = resolvedAnimeTitleMaxLines(
            textLength = text.length,
            baseMaxLines = baseMaxLines,
            extraLongTitleLines = extraLongTitleLines,
        ),
        overflow = overflow,
        onTextLayout = onTextLayout ?: {},
    )
}

@Composable
fun AnimeTitleText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    minLines: Int = 1,
    baseMaxLines: Int = 2,
    extraLongTitleLines: Int = 2,
    overflow: TextOverflow = TextOverflow.Clip,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
) {
    Text(
        text = text,
        inlineContent = inlineContent,
        modifier = modifier,
        style = style,
        color = color,
        textAlign = textAlign,
        minLines = minLines,
        maxLines = resolvedAnimeTitleMaxLines(
            textLength = text.text.length,
            baseMaxLines = baseMaxLines,
            extraLongTitleLines = extraLongTitleLines,
        ),
        overflow = overflow,
        onTextLayout = onTextLayout ?: {},
    )
}

private fun resolvedAnimeTitleMaxLines(
    textLength: Int,
    baseMaxLines: Int,
    extraLongTitleLines: Int,
): Int {
    val normalizedBaseLines = baseMaxLines.coerceAtLeast(1)
    val normalizedExtraLines = extraLongTitleLines.coerceAtLeast(0)
    return when {
        textLength >= 72 -> normalizedBaseLines + normalizedExtraLines
        textLength >= 42 -> normalizedBaseLines + 1
        else -> normalizedBaseLines
    }
}

package org.akkirrai.hibiki.core.design.component

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun AnimeTitleText(text: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current, color: Color = Color.Unspecified, textAlign: TextAlign? = null, minLines: Int = 1, baseMaxLines: Int = 2, extraLongTitleLines: Int = 2, overflow: TextOverflow = TextOverflow.Clip, onTextLayout: ((TextLayoutResult) -> Unit)? = null) = org.akkirrai.hibiki.shared.design.component.AnimeTitleText(text, modifier, style, color, textAlign, minLines, baseMaxLines, extraLongTitleLines, overflow, onTextLayout)

@Composable
fun AnimeTitleText(text: AnnotatedString, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current, color: Color = Color.Unspecified, textAlign: TextAlign? = null, minLines: Int = 1, baseMaxLines: Int = 2, extraLongTitleLines: Int = 2, overflow: TextOverflow = TextOverflow.Clip, onTextLayout: ((TextLayoutResult) -> Unit)? = null, inlineContent: Map<String, InlineTextContent> = mapOf()) = org.akkirrai.hibiki.shared.design.component.AnimeTitleText(text, modifier, style, color, textAlign, minLines, baseMaxLines, extraLongTitleLines, overflow, onTextLayout, inlineContent)

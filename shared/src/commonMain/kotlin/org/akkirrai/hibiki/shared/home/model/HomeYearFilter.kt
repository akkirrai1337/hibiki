package org.akkirrai.hibiki.shared.home.model

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.design.component.filter.AppCollapsibleFilterSection
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppHomeYearFilter(
    selectedRange: IntRange,
    yearRange: IntRange,
    title: String,
    allLabel: String,
    fromLabel: String,
    toLabel: String,
    onRangeChange: (IntRange) -> Unit,
    arrowContent: @Composable (Modifier) -> Unit,
) {
    val animatedFromYear by animateIntAsState(selectedRange.first, label = "year_from_value")
    val animatedToYear by animateIntAsState(selectedRange.last, label = "year_to_value")
    var sliderPosition by remember {
        mutableStateOf(selectedRange.first.toFloat()..selectedRange.last.toFloat())
    }
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(selectedRange) {
        if (!isDragging) {
            sliderPosition = selectedRange.first.toFloat()..selectedRange.last.toFloat()
        }
    }
    AppCollapsibleFilterSection(
        title = title,
        onLongClick = { onRangeChange(yearRange) },
        arrowContent = arrowContent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = UiDimens.FilterContentTopPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (selectedRange == yearRange) {
                Text(
                    text = allLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = UiDimens.YearFilterAllFontSize,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "$fromLabel $animatedFromYear",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = UiDimens.YearFilterSelectedFontSize,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "$toLabel $animatedToYear",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = UiDimens.YearFilterSelectedFontSize,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = yearRange.first.toString(),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    fontSize = UiDimens.YearFilterBoundaryFontSize,
                    fontWeight = FontWeight.SemiBold,
                )
                RangeSlider(
                    value = sliderPosition,
                    onValueChange = { range ->
                        isDragging = true
                        sliderPosition = range
                        onRangeChange(range.start.roundToInt()..range.endInclusive.roundToInt())
                    },
                    onValueChangeFinished = { isDragging = false },
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        inactiveTickColor = Color.Transparent,
                        activeTickColor = Color.Transparent,
                    ),
                    steps = (yearRange.count() - 2).coerceAtLeast(0),
                    valueRange = yearRange.first.toFloat()..yearRange.last.toFloat(),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = yearRange.last.toString(),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    fontSize = UiDimens.YearFilterBoundaryFontSize,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

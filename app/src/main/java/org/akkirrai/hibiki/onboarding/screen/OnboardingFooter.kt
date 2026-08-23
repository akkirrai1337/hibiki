package org.akkirrai.hibiki.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppOnboardingFooter(
    currentStep: Int,
    stepCount: Int,
    showNavigation: Boolean,
    nextEnabled: Boolean,
    backLabel: String,
    nextLabel: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(88.dp), contentAlignment = Alignment.CenterStart) {
            if (showNavigation) TextButton(onClick = onBack) { Text(backLabel) }
        }
        AppOnboardingStepIndicator(
            currentStep = currentStep,
            stepCount = stepCount,
            modifier = Modifier.weight(1f),
        )
        Box(modifier = Modifier.width(88.dp), contentAlignment = Alignment.CenterEnd) {
            if (showNavigation) TextButton(onClick = onNext, enabled = nextEnabled) { Text(nextLabel) }
        }
    }
}

@Composable
private fun AppOnboardingStepIndicator(
    currentStep: Int,
    stepCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(stepCount) { index ->
            val isCurrent = index == currentStep
            val indicatorWidth by animateDpAsState(
                targetValue = if (isCurrent) 28.dp else 8.dp,
                animationSpec = tween(durationMillis = 250),
                label = "onboarding_indicator_width_$index",
            )
            Surface(
                modifier = Modifier.size(width = indicatorWidth, height = 8.dp),
                shape = CircleShape,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                content = {},
            )
        }
    }
}

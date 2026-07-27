package org.akkirrai.hibiki.shared.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

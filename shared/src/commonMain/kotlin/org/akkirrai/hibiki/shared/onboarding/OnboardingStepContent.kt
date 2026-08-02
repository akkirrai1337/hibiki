package org.akkirrai.hibiki.shared.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun AppOnboardingStepContent(
    step: OnboardingStep,
    modifier: Modifier = Modifier,
    welcomeContent: @Composable () -> Unit,
    sourceContent: @Composable () -> Unit,
    notificationsContent: @Composable () -> Unit,
) {
    AnimatedContent(
        targetState = step,
        modifier = modifier,
        transitionSpec = {
            val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
            (
                (slideInHorizontally(
                    animationSpec = tween(260),
                    initialOffsetX = { width -> direction * width / 4 },
                ) + fadeIn(animationSpec = tween(260))) togetherWith
                    (slideOutHorizontally(
                        animationSpec = tween(220),
                        targetOffsetX = { width -> -direction * width / 4 },
                    ) + fadeOut(animationSpec = tween(220)))
                ).using(SizeTransform(clip = false))
        },
        contentAlignment = Alignment.Center,
        label = "onboarding_step",
    ) { currentStep ->
        when (currentStep) {
            OnboardingStep.WELCOME -> welcomeContent()
            OnboardingStep.SOURCE -> sourceContent()
            OnboardingStep.NOTIFICATIONS -> notificationsContent()
        }
    }
}

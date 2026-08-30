package org.akkirrai.hibiki.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.NotificationPermissionState

private enum class OnboardingStep {
    WELCOME,
    NOTIFICATIONS,
}

@Composable
fun FirstLaunchOnboarding(
    notificationPermissionState: NotificationPermissionState,
    onRequestNotificationPermission: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var stepName by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME.name) }
    val step = OnboardingStep.valueOf(stepName)
    BackHandler(enabled = step != OnboardingStep.WELCOME) {
        stepName = when (step) {
            OnboardingStep.WELCOME -> OnboardingStep.WELCOME.name
            OnboardingStep.NOTIFICATIONS -> OnboardingStep.WELCOME.name
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f),
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
                    OnboardingStep.WELCOME -> WelcomeStep(
                        onStart = { stepName = OnboardingStep.NOTIFICATIONS.name },
                        modifier = Modifier.fillMaxSize(),
                    )

                    OnboardingStep.NOTIFICATIONS -> NotificationsStep(
                        permissionState = notificationPermissionState,
                        onRequestPermission = onRequestNotificationPermission,
                        modifier = Modifier.fillMaxSize(),
                    )

                }
            }

            // Keep the footer mounted on every step. Removing it on the welcome
            // page changes the AnimatedContent height during the first transition
            // and makes the outgoing page visibly jump upward.
            OnboardingFooter(
                step = step,
                onBack = {
                    stepName = when (step) {
                        OnboardingStep.WELCOME -> OnboardingStep.WELCOME.name
                        OnboardingStep.NOTIFICATIONS -> OnboardingStep.WELCOME.name
                    }
                },
                onNext = {
                    when (step) {
                        OnboardingStep.WELCOME -> stepName = OnboardingStep.NOTIFICATIONS.name
                        OnboardingStep.NOTIFICATIONS -> onComplete()
                        }
                },
            )
        }
    }

}

@Composable
private fun WelcomeStep(
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(156.dp),
            shape = CircleShape,
            color = Color.White,
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = stringResource(R.string.app_name),
                // The foreground layer reserves an adaptive-icon safe zone (padding baked into
                // its 108dp canvas for the launcher's mask), so it renders visibly smaller than
                // a flat icon here where nothing masks it -- scale up to crop that margin away.
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.5f),
                contentScale = ContentScale.Fit,
            )
        }
        Spacer(Modifier.height(40.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(36.dp))
        Button(onClick = onStart) {
            Text(stringResource(R.string.onboarding_get_started))
        }
    }
}

@Composable
private fun NotificationsStep(
    permissionState: NotificationPermissionState,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.NotificationsActive,
            contentDescription = null,
            modifier = Modifier.size(112.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.onboarding_notifications_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.onboarding_notifications_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        when (permissionState) {
            NotificationPermissionState.NOT_ASKED -> {
                Button(onClick = onRequestPermission) {
                    Text(stringResource(R.string.onboarding_notifications_allow))
                }
            }

            NotificationPermissionState.GRANTED -> PermissionStatus(
                text = stringResource(R.string.onboarding_notifications_enabled),
            )

            NotificationPermissionState.DENIED -> PermissionStatus(
                text = stringResource(R.string.onboarding_notifications_denied),
            )
        }
    }
}

@Composable
private fun PermissionStatus(text: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OnboardingFooter(
    step: OnboardingStep,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The navigation buttons make non-welcome steps taller. Reserve their
            // height on every step so the weighted page area never resizes while
            // AnimatedContent is transitioning.
            .height(72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(88.dp), contentAlignment = Alignment.CenterStart) {
            if (step != OnboardingStep.WELCOME) {
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.onboarding_back))
                }
            }
        }
        StepIndicator(
            currentStep = step.ordinal,
            stepCount = OnboardingStep.entries.size,
            modifier = Modifier.weight(1f),
        )
        Box(modifier = Modifier.width(88.dp), contentAlignment = Alignment.CenterEnd) {
            if (step != OnboardingStep.WELCOME) {
                TextButton(
                    onClick = onNext,
                ) {
                    Text(
                        stringResource(
                            if (step == OnboardingStep.NOTIFICATIONS) {
                                R.string.onboarding_done
                            } else {
                                R.string.onboarding_next
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(
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
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                content = {},
            )
        }
    }
}


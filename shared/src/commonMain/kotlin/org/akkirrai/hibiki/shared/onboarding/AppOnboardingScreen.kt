package org.akkirrai.hibiki.shared.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.settings.NotificationPermissionState

data class OnboardingSourceOption(
    val id: String,
    val name: String,
    val languageSummary: String,
)

data class AppOnboardingScreenLabels(
    val welcomeTitle: String,
    val welcomeDescription: String,
    val getStarted: String,
    val sourceTitle: String,
    val sourceDescription: String,
    val sourceNoMatch: String,
    val viewAllSources: String,
    val notificationsTitle: String,
    val notificationsDescription: String,
    val notificationsAllow: String,
    val notificationsEnabled: String,
    val notificationsDenied: String,
    val back: String,
    val next: String,
    val done: String,
)

data class AppOnboardingScreenIcons(
    val source: ImageVector,
    val notifications: ImageVector,
)

/** Shared render layer for the first-launch flow. */
@Composable
fun AppOnboardingScreen(
    step: OnboardingStep,
    sources: List<OnboardingSourceOption>,
    selectedSourceId: String?,
    notificationPermissionState: NotificationPermissionState,
    labels: AppOnboardingScreenLabels,
    icons: AppOnboardingScreenIcons,
    appIconContent: @Composable () -> Unit,
    sourceIconContent: @Composable (OnboardingSourceOption) -> Unit,
    onStart: () -> Unit,
    onSourceSelected: (OnboardingSourceOption) -> Unit,
    onShowAllSources: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                    (
                        (slideInHorizontally(initialOffsetX = { width -> direction * width / 4 }) + fadeIn()) togetherWith
                            (slideOutHorizontally(targetOffsetX = { width -> -direction * width / 4 }) + fadeOut())
                        ).using(SizeTransform(clip = false))
                },
                contentAlignment = Alignment.Center,
                label = "onboarding_step",
            ) { currentStep ->
                when (currentStep) {
                    OnboardingStep.WELCOME -> WelcomeContent(
                        labels = labels,
                        appIconContent = appIconContent,
                        onStart = onStart,
                        modifier = Modifier.fillMaxSize(),
                    )

                    OnboardingStep.SOURCE -> SourceContent(
                        sources = sources,
                        selectedSourceId = selectedSourceId,
                        labels = labels,
                        icon = icons.source,
                        sourceIconContent = sourceIconContent,
                        onSourceSelected = onSourceSelected,
                        onShowAllSources = onShowAllSources,
                        modifier = Modifier.fillMaxSize(),
                    )

                    OnboardingStep.NOTIFICATIONS -> NotificationContent(
                        permissionState = notificationPermissionState,
                        labels = labels,
                        icon = icons.notifications,
                        onRequestPermission = onRequestNotificationPermission,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            OnboardingFooter(
                step = step,
                nextEnabled = selectedSourceId != null,
                labels = labels,
                onBack = onBack,
                onNext = onNext,
            )
        }
    }
}

@Composable
private fun WelcomeContent(
    labels: AppOnboardingScreenLabels,
    appIconContent: @Composable () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(Modifier.size(156.dp), shape = CircleShape, color = androidx.compose.ui.graphics.Color.White) {
            Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) { appIconContent() }
        }
        Spacer(Modifier.height(40.dp))
        Text(labels.welcomeTitle, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(labels.welcomeDescription, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(36.dp))
        Button(onClick = onStart) { Text(labels.getStarted) }
    }
}

@Composable
private fun SourceContent(
    sources: List<OnboardingSourceOption>,
    selectedSourceId: String?,
    labels: AppOnboardingScreenLabels,
    icon: ImageVector,
    sourceIconContent: @Composable (OnboardingSourceOption) -> Unit,
    onSourceSelected: (OnboardingSourceOption) -> Unit,
    onShowAllSources: () -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        item {
            Icon(icon, null, Modifier.size(88.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(20.dp))
            Text(labels.sourceTitle, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(
                if (sources.isEmpty()) labels.sourceNoMatch else labels.sourceDescription,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
        }
        items(sources, key = OnboardingSourceOption::id) { source ->
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable { onSourceSelected(source) },
                shape = RoundedCornerShape(24.dp),
                color = if (source.id == selectedSourceId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { sourceIconContent(source) }
                    Column(Modifier.weight(1f)) {
                        Text(source.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(source.languageSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RadioButton(selected = source.id == selectedSourceId, onClick = null)
                }
            }
        }
        item { TextButton(onClick = onShowAllSources) { Text(labels.viewAllSources) } }
    }
}

@Composable
private fun NotificationContent(
    permissionState: NotificationPermissionState,
    labels: AppOnboardingScreenLabels,
    icon: ImageVector,
    onRequestPermission: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(112.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(32.dp))
        Text(labels.notificationsTitle, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Text(labels.notificationsDescription, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        when (permissionState) {
            NotificationPermissionState.NOT_ASKED -> Button(onClick = onRequestPermission) { Text(labels.notificationsAllow) }
            NotificationPermissionState.GRANTED -> PermissionStatus(labels.notificationsEnabled)
            NotificationPermissionState.DENIED -> PermissionStatus(labels.notificationsDenied)
        }
    }
}

@Composable
private fun PermissionStatus(text: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Text(text, Modifier.padding(horizontal = 20.dp, vertical = 14.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun OnboardingFooter(
    step: OnboardingStep,
    nextEnabled: Boolean,
    labels: AppOnboardingScreenLabels,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(88.dp), contentAlignment = Alignment.CenterStart) {
            if (step != OnboardingStep.WELCOME) TextButton(onClick = onBack) { Text(labels.back) }
        }
        StepIndicator(step.ordinal, OnboardingStep.entries.size, Modifier.weight(1f))
        Box(Modifier.width(88.dp), contentAlignment = Alignment.CenterEnd) {
            if (step != OnboardingStep.WELCOME) TextButton(onClick = onNext, enabled = step == OnboardingStep.NOTIFICATIONS || nextEnabled) {
                Text(if (step == OnboardingStep.NOTIFICATIONS) labels.done else labels.next)
            }
        }
    }
}

@Composable
private fun StepIndicator(currentStep: Int, stepCount: Int, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        repeat(stepCount) { index ->
            Surface(
                Modifier.size(width = if (index == currentStep) 28.dp else 8.dp, height = 8.dp),
                shape = CircleShape,
                color = if (index == currentStep) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            ) {}
        }
    }
}

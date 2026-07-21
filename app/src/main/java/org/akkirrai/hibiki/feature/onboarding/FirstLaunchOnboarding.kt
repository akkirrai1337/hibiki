package org.akkirrai.hibiki.feature.onboarding

import android.os.LocaleList
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.NotificationPermissionState
import org.akkirrai.hibiki.core.source.AnimeSourceDescriptor
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry
import org.akkirrai.hibiki.feature.settings.SourcesScreen

private enum class OnboardingStep {
    WELCOME,
    SOURCE,
    NOTIFICATIONS,
}

@Composable
fun FirstLaunchOnboarding(
    initialSource: SourceId?,
    notificationPermissionState: NotificationPermissionState,
    onRequestNotificationPermission: () -> Unit,
    onComplete: (SourceId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val allSources = AnimeSourceRegistry.sources
    val systemLanguage = remember { LocaleList.getDefault().get(0)?.language.orEmpty() }
    val localizedSources = remember(allSources, systemLanguage) {
        onboardingSourcesForSystemLanguage(allSources, systemLanguage)
    }
    var stepName by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME.name) }
    val step = OnboardingStep.valueOf(stepName)
    var selectedSourceValue by rememberSaveable { mutableStateOf(initialSource?.value) }
    var showSourceList by rememberSaveable { mutableStateOf(false) }
    val selectedSource = selectedSourceValue?.let(::SourceId)
    val displayedSources = remember(localizedSources, selectedSourceValue, allSources) {
        val selected = allSources.firstOrNull { it.id.value == selectedSourceValue }
        if (selected != null && selected !in localizedSources) {
            listOf(selected) + localizedSources
        } else {
            localizedSources
        }
    }

    LaunchedEffect(localizedSources, initialSource) {
        if (selectedSourceValue == null && localizedSources.size == 1) {
            selectedSourceValue = localizedSources.single().id.value
        }
    }
    LaunchedEffect(step, localizedSources) {
        if (step == OnboardingStep.SOURCE && localizedSources.isEmpty()) {
            showSourceList = true
        }
    }

    BackHandler(enabled = showSourceList) {
        showSourceList = false
    }
    BackHandler(enabled = !showSourceList && step != OnboardingStep.WELCOME) {
        stepName = when (step) {
            OnboardingStep.WELCOME -> OnboardingStep.WELCOME.name
            OnboardingStep.SOURCE -> OnboardingStep.WELCOME.name
            OnboardingStep.NOTIFICATIONS -> OnboardingStep.SOURCE.name
        }
    }

    if (showSourceList) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            SourcesScreen(
                selectedSourceOverride = selectedSource,
                onSourceSelected = { selectedSourceValue = it.value },
                onBackClick = { showSourceList = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
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
                    (slideInHorizontally(
                        animationSpec = tween(260),
                        initialOffsetX = { width -> direction * width / 4 },
                    ) + fadeIn(animationSpec = tween(260))) togetherWith
                        (slideOutHorizontally(
                            animationSpec = tween(220),
                            targetOffsetX = { width -> -direction * width / 4 },
                        ) + fadeOut(animationSpec = tween(220)))
                },
                label = "onboarding_step",
            ) { currentStep ->
                when (currentStep) {
                    OnboardingStep.WELCOME -> WelcomeStep(
                        onStart = { stepName = OnboardingStep.SOURCE.name },
                        modifier = Modifier.fillMaxSize(),
                    )

                    OnboardingStep.SOURCE -> SourceStep(
                        sources = displayedSources,
                        selectedSource = selectedSource,
                        localizedSourcesMissing = localizedSources.isEmpty(),
                        onSourceSelected = { selectedSourceValue = it.id.value },
                        onShowAllSources = { showSourceList = true },
                        modifier = Modifier.fillMaxSize(),
                    )

                    OnboardingStep.NOTIFICATIONS -> NotificationsStep(
                        permissionState = notificationPermissionState,
                        onRequestPermission = onRequestNotificationPermission,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            OnboardingFooter(
                step = step,
                nextEnabled = selectedSource != null,
                onBack = {
                    stepName = when (step) {
                        OnboardingStep.WELCOME -> OnboardingStep.WELCOME.name
                        OnboardingStep.SOURCE -> OnboardingStep.WELCOME.name
                        OnboardingStep.NOTIFICATIONS -> OnboardingStep.SOURCE.name
                    }
                },
                onNext = {
                    when (step) {
                        OnboardingStep.WELCOME -> stepName = OnboardingStep.SOURCE.name
                        OnboardingStep.SOURCE -> stepName = OnboardingStep.NOTIFICATIONS.name
                        OnboardingStep.NOTIFICATIONS -> selectedSource?.let(onComplete)
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
        Image(
            painter = painterResource(R.drawable.hibiki_app_icon),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier
                .size(156.dp)
                .clip(CircleShape),
        )
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
private fun SourceStep(
    sources: List<AnimeSourceDescriptor>,
    selectedSource: SourceId?,
    localizedSourcesMissing: Boolean,
    onSourceSelected: (AnimeSourceDescriptor) -> Unit,
    onShowAllSources: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Icon(
                imageVector = Icons.Rounded.VideoLibrary,
                contentDescription = null,
                modifier = Modifier.size(88.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.onboarding_source_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(
                    if (localizedSourcesMissing) {
                        R.string.onboarding_source_no_match
                    } else {
                        R.string.onboarding_source_description
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
        }
        items(sources, key = { it.id.value }) { source ->
            SourceChoiceCard(
                source = source,
                selected = source.id == selectedSource,
                onClick = { onSourceSelected(source) },
            )
        }
        item {
            TextButton(onClick = onShowAllSources) {
                Text(stringResource(R.string.onboarding_view_all_sources))
            }
        }
    }
}

@Composable
private fun NotificationsStep(
    permissionState: NotificationPermissionState,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var skipped by rememberSaveable { mutableStateOf(false) }
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
                if (skipped) {
                    PermissionStatus(
                        text = stringResource(R.string.onboarding_notifications_skipped),
                    )
                } else {
                    Button(onClick = onRequestPermission) {
                        Text(stringResource(R.string.onboarding_notifications_allow))
                    }
                    TextButton(onClick = { skipped = true }) {
                        Text(stringResource(R.string.onboarding_notifications_not_now))
                    }
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
    nextEnabled: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                    enabled = step == OnboardingStep.NOTIFICATIONS || nextEnabled,
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
            Surface(
                modifier = Modifier.size(width = if (index == currentStep) 28.dp else 8.dp, height = 8.dp),
                shape = CircleShape,
                color = if (index == currentStep) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                content = {},
            )
        }
    }
}

@Composable
private fun SourceChoiceCard(
    source: AnimeSourceDescriptor,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = source.iconUrl,
                placeholder = painterResource(source.iconRes),
                error = painterResource(source.iconRes),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = sourceLanguageSummary(source),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@Composable
private fun sourceLanguageSummary(source: AnimeSourceDescriptor): String {
    val languages = source.info.languages
    return when {
        SourceLanguage.RUSSIAN in languages && SourceLanguage.ENGLISH in languages -> {
            stringResource(R.string.onboarding_source_languages_ru_en)
        }
        SourceLanguage.RUSSIAN in languages -> stringResource(R.string.onboarding_source_language_ru)
        SourceLanguage.ENGLISH in languages -> stringResource(R.string.onboarding_source_language_en)
        else -> languages.joinToString { it.tag.uppercase() }
    }
}

internal fun onboardingSourcesForSystemLanguage(
    sources: List<AnimeSourceDescriptor>,
    systemLanguage: String,
): List<AnimeSourceDescriptor> {
    val preferredLanguage = if (systemLanguage.lowercase() in setOf("ru", "uk", "be")) {
        SourceLanguage.RUSSIAN
    } else {
        SourceLanguage.ENGLISH
    }
    return sources.filter { source -> preferredLanguage in source.info.languages }
}

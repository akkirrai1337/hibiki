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
import androidx.compose.animation.SizeTransform
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.NotificationPermissionState
import org.akkirrai.hibiki.core.source.AnimeSourceDescriptor
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry
import org.akkirrai.hibiki.feature.settings.SourcesScreen
import org.akkirrai.hibiki.shared.onboarding.OnboardingStep
import org.akkirrai.hibiki.shared.onboarding.AppOnboardingStepIndicator
import org.akkirrai.hibiki.shared.onboarding.AppOnboardingSourceCard
import org.akkirrai.hibiki.shared.onboarding.AppOnboardingFooter
import org.akkirrai.hibiki.shared.onboarding.AppOnboardingWelcome
import org.akkirrai.hibiki.shared.onboarding.AppOnboardingNotifications
import org.akkirrai.hibiki.shared.onboarding.AppOnboardingPermissionStatus
import org.akkirrai.hibiki.shared.onboarding.AppOnboardingSourceStep

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
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
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
                    OnboardingStep.WELCOME -> AppOnboardingWelcome(
                        title = stringResource(R.string.onboarding_welcome_title),
                        description = stringResource(R.string.onboarding_welcome_description),
                        buttonLabel = stringResource(R.string.onboarding_get_started),
                        onStart = { stepName = OnboardingStep.SOURCE.name },
                        modifier = Modifier.fillMaxSize(),
                        appIconContent = {
                            androidx.compose.foundation.Image(
                                painter = painterResource(R.drawable.hibiki_app_icon),
                                contentDescription = stringResource(R.string.app_name),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                contentScale = ContentScale.Fit,
                            )
                        },
                    )

                    OnboardingStep.SOURCE -> AppOnboardingSourceStep(
                        title = stringResource(R.string.onboarding_source_title),
                        description = stringResource(
                            if (localizedSources.isEmpty()) {
                                R.string.onboarding_source_no_match
                            } else {
                                R.string.onboarding_source_description
                            },
                        ),
                        icon = Icons.Rounded.VideoLibrary,
                        items = displayedSources,
                        itemKey = { it.id.value },
                        modifier = Modifier.fillMaxSize(),
                        itemContent = { source ->
                            AppOnboardingSourceCard(
                                name = source.name,
                                languageSummary = sourceLanguageSummary(source),
                                selected = source.id == selectedSource,
                                onClick = { selectedSourceValue = source.id.value },
                                iconContent = {
                                    AsyncImage(
                                        model = source.iconUrl,
                                        placeholder = painterResource(source.iconRes),
                                        error = painterResource(source.iconRes),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape),
                                    )
                                },
                            )
                        },
                        footerContent = {
                            TextButton(onClick = { showSourceList = true }) {
                                Text(stringResource(R.string.onboarding_view_all_sources))
                            }
                        },
                    )

                    OnboardingStep.NOTIFICATIONS -> AppOnboardingNotifications(
                        title = stringResource(R.string.onboarding_notifications_title),
                        description = stringResource(R.string.onboarding_notifications_description),
                        icon = Icons.Rounded.NotificationsActive,
                        modifier = Modifier.fillMaxSize(),
                        actionContent = {
                            when (notificationPermissionState) {
                                NotificationPermissionState.NOT_ASKED -> {
                                    Button(onClick = onRequestNotificationPermission) {
                                        Text(stringResource(R.string.onboarding_notifications_allow))
                                    }
                                }
                                NotificationPermissionState.GRANTED -> AppOnboardingPermissionStatus(
                                    text = stringResource(R.string.onboarding_notifications_enabled),
                                )
                                NotificationPermissionState.DENIED -> AppOnboardingPermissionStatus(
                                    text = stringResource(R.string.onboarding_notifications_denied),
                                )
                            }
                        },
                    )

                }
            }

            // Keep the footer mounted on every step. Removing it on the welcome
            // page changes the AnimatedContent height during the first transition
            // and makes the outgoing page visibly jump upward.
            AppOnboardingFooter(
                currentStep = step.ordinal,
                stepCount = OnboardingStep.entries.size,
                showNavigation = step != OnboardingStep.WELCOME,
                nextEnabled = step == OnboardingStep.NOTIFICATIONS || selectedSource != null,
                backLabel = stringResource(R.string.onboarding_back),
                nextLabel = stringResource(
                    if (step == OnboardingStep.NOTIFICATIONS) R.string.onboarding_done else R.string.onboarding_next,
                ),
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

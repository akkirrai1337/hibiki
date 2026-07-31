package org.akkirrai.hibiki.shared.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import hibiki.shared.generated.resources.Res
import hibiki.shared.generated.resources.hibiki_app_icon
import org.akkirrai.hibiki.shared.settings.NotificationPermissionState
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor
import org.akkirrai.hibiki.shared.source.AppSourceIconImage
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText
import org.jetbrains.compose.resources.painterResource

@Composable
fun AppOnboardingScreen(
    sources: List<AppSourceDescriptor>,
    initialSourceId: String?,
    systemLanguage: String = "en",
    notificationPermissionState: NotificationPermissionState,
    onRequestNotificationPermission: () -> Unit,
    onComplete: (String) -> Unit,
    sourceIconContent: @Composable (AppSourceDescriptor, Modifier) -> Unit = { source, iconModifier ->
        AppSourceIconImage(
            url = source.iconUrl,
            modifier = iconModifier,
        )
    },
    modifier: Modifier = Modifier,
) {
    var stepName by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME.name) }
    var selectedSourceId by rememberSaveable { mutableStateOf(initialSourceId) }
    val step = OnboardingStep.valueOf(stepName)
    val displayedSources = includeSelectedOnboardingSource(
        allSources = sources,
        visibleSources = filterOnboardingSourcesByLanguage(
            sources = sources,
            systemLanguage = systemLanguage,
            russianTag = "ru",
            englishTag = "en",
            languageTags = AppSourceDescriptor::languageTags,
        ),
        selectedKey = initialSourceId,
        keyOf = AppSourceDescriptor::id,
    )

    LaunchedEffect(displayedSources, initialSourceId) {
        if (selectedSourceId == null && displayedSources.size == 1) {
            selectedSourceId = displayedSources.single().id
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
            AppOnboardingStepContent(
                step = step,
                modifier = Modifier.weight(1f),
                welcomeContent = {
                    AppOnboardingWelcome(
                        title = appText(AppTextKey.OnboardingWelcomeTitle),
                        description = appText(AppTextKey.OnboardingWelcomeDescription),
                        buttonLabel = appText(AppTextKey.OnboardingGetStarted),
                        onStart = { stepName = OnboardingStep.SOURCE.name },
                        modifier = Modifier.fillMaxSize(),
                        appIconContent = { iconModifier ->
                            Image(
                                painter = painterResource(Res.drawable.hibiki_app_icon),
                                contentDescription = appText(AppTextKey.AppName),
                                modifier = iconModifier,
                                contentScale = ContentScale.Fit,
                            )
                        },
                    )
                },
                sourceContent = {
                    AppOnboardingSourceStep(
                        title = appText(AppTextKey.OnboardingSourceTitle),
                        description = appText(AppTextKey.OnboardingSourceDescription),
                        items = displayedSources,
                        itemKey = AppSourceDescriptor::id,
                        itemContent = { source ->
                            AppOnboardingSourceCard(
                                name = source.name,
                                languageSummary = sourceLanguageSummary(
                                    languageTags = source.languageTags,
                                    fallbackLanguage = source.language,
                                ),
                                selected = source.id == selectedSourceId,
                                onClick = { selectedSourceId = source.id },
                                iconContent = { iconModifier ->
                                    sourceIconContent(source, iconModifier)
                                },
                            )
                        },
                        footerContent = {},
                    )
                },
                notificationsContent = {
                    AppOnboardingNotifications(
                        title = appText(AppTextKey.OnboardingNotificationsTitle),
                        description = appText(AppTextKey.OnboardingNotificationsDescription),
                        modifier = Modifier.fillMaxSize(),
                        actionContent = {
                            when (notificationPermissionState) {
                                NotificationPermissionState.NOT_ASKED -> {
                                    Button(onClick = onRequestNotificationPermission) {
                                        Text(appText(AppTextKey.OnboardingNotificationsAllow))
                                    }
                                }
                                NotificationPermissionState.GRANTED -> {
                                    AppOnboardingPermissionStatus(
                                        text = appText(AppTextKey.OnboardingNotificationsEnabled),
                                    )
                                }
                                NotificationPermissionState.DENIED -> {
                                    AppOnboardingPermissionStatus(
                                        text = appText(AppTextKey.OnboardingNotificationsDenied),
                                    )
                                }
                            }
                        },
                    )
                },
            )
            AppOnboardingFooter(
                currentStep = step.ordinal,
                stepCount = OnboardingStep.entries.size,
                showNavigation = step != OnboardingStep.WELCOME,
                nextEnabled = step == OnboardingStep.NOTIFICATIONS || selectedSourceId != null,
                backLabel = appText(AppTextKey.OnboardingBack),
                nextLabel = if (step == OnboardingStep.NOTIFICATIONS) {
                    appText(AppTextKey.OnboardingDone)
                } else {
                    appText(AppTextKey.OnboardingNext)
                },
                onBack = { step.previous()?.let { stepName = it.name } },
                onNext = {
                    step.next()?.let { stepName = it.name }
                        ?: selectedSourceId?.let(onComplete)
                },
            )
        }
    }
}

@Composable
private fun sourceLanguageSummary(languageTags: Set<String>, fallbackLanguage: String): String {
    val language = languageTags.firstOrNull().orEmpty().ifBlank { fallbackLanguage }
    val normalizedTags = languageTags.ifEmpty { setOf(fallbackLanguage) }
    return when {
        normalizedTags.size > 1 && normalizedTags.all { it.lowercase() in setOf("ru", "en") } ->
            appText(AppTextKey.OnboardingSourceLanguagesRussianEnglish)
        language.lowercase() == "ru" -> appText(AppTextKey.OnboardingSourceLanguageRussian)
        language.lowercase() == "en" -> appText(AppTextKey.OnboardingSourceLanguageEnglish)
        else -> language
    }
}

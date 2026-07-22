package org.akkirrai.hibiki.shared.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.UiDimens

data class AppSettingsCardLabels(
    val title: String,
    val description: String,
    val languageSystem: String,
    val languageEnglish: String,
    val languageRussian: String,
    val themeLight: String,
    val themeDark: String,
)

@Composable
fun AppSettingsCard(
    languageMode: LanguageMode,
    onLanguageModeChange: (LanguageMode) -> Unit,
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    labels: AppSettingsCardLabels,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(UiDimens.MediumCorner),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(labels.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(labels.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            TextButton(
                onClick = {
                    onLanguageModeChange(
                        when (languageMode) {
                            LanguageMode.RUSSIAN -> LanguageMode.ENGLISH
                            LanguageMode.ENGLISH -> LanguageMode.RUSSIAN
                            LanguageMode.SYSTEM -> LanguageMode.RUSSIAN
                        },
                    )
                },
            ) {
                Text(
                    when (languageMode) {
                        LanguageMode.SYSTEM -> labels.languageSystem
                        LanguageMode.ENGLISH -> labels.languageEnglish
                        LanguageMode.RUSSIAN -> labels.languageRussian
                    },
                )
            }
            TextButton(onClick = { onThemeChange(!darkTheme) }) {
                Text(if (darkTheme) labels.themeDark else labels.themeLight)
            }
        }
    }
}

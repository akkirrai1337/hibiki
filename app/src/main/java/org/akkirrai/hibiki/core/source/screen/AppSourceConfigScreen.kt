package org.akkirrai.hibiki.core.source

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.akkirrai.beakokit.api.MapSourceConfig
import org.akkirrai.beakokit.api.SourceConfigField
import org.akkirrai.beakokit.api.SourceConfigSchema
import org.akkirrai.beakokit.api.SourceConfigValueKind

data class AppSourceConfigLabels(
    val fieldLabel: (SourceConfigField) -> String,
    val saveLabel: String,
    val cancelLabel: String,
    val validationErrorLabel: (String) -> String = { it },
)

typealias AppSourceConfigContent = @Composable (
    AppSourceDescriptor,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
) -> Unit

val LocalAppSourceConfigContent = staticCompositionLocalOf<AppSourceConfigContent?> { null }

/** Shared editor for one source's declared configuration schema. */
@Composable
fun AppSourceConfigScreen(
    schema: SourceConfigSchema,
    initialValues: Map<String, String> = emptyMap(),
    initialSecrets: Map<String, String> = emptyMap(),
    labels: AppSourceConfigLabels,
    onSave: (values: Map<String, String>, secrets: Map<String, String>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var values by remember(schema, initialValues) { mutableStateOf(initialValues.toMap()) }
    var secrets by remember(schema, initialSecrets) { mutableStateOf(initialSecrets.toMap()) }
    var violations by remember { mutableStateOf(emptyList<String>()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        schema.fields.forEach { field ->
            val isSecret = field.kind == SourceConfigValueKind.SECRET
            val fieldValues = if (isSecret) secrets else values
            OutlinedTextField(
                value = fieldValues[field.key].orEmpty(),
                onValueChange = { value ->
                    if (isSecret) {
                        secrets = secrets + (field.key to value)
                    } else {
                        values = values + (field.key to value)
                    }
                    violations = emptyList()
                },
                label = { Text(labels.fieldLabel(field)) },
                singleLine = true,
                visualTransformation = if (isSecret) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        violations.forEach { violation ->
            Text(labels.validationErrorLabel(violation))
        }
        Button(
            onClick = {
                val configViolations = schema.violations(MapSourceConfig(values, secrets))
                if (configViolations.isEmpty()) {
                    onSave(values, secrets)
                } else {
                    violations = configViolations
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(labels.saveLabel)
        }
        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(labels.cancelLabel)
        }
    }
}

package org.akkirrai.hibiki.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.akkirrai.beakokit.model.SourceFilterDef
import org.akkirrai.beakokit.model.SourceFilterType
import org.akkirrai.hibiki.core.design.component.filter.AppCollapsibleFilterSection

/**
 * Renders the filters a source defines for itself. Values are kept as the strings described on
 * [SourceFilterType], keyed by [SourceFilterDef.key], and only differences from a filter default are
 * stored, so an untouched sheet produces an empty map (which means "no filters").
 */
@Composable
fun SourceFilterControls(
    filters: List<SourceFilterDef>,
    values: Map<String, String>,
    onValuesChange: (Map<String, String>) -> Unit,
) {
    filters.forEach { def -> SourceFilter(def, values, onValuesChange) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceFilter(
    def: SourceFilterDef,
    values: Map<String, String>,
    onValuesChange: (Map<String, String>) -> Unit,
) {
    fun current() = values[def.key] ?: def.defaultValue
    fun set(value: String) =
        onValuesChange(if (value == def.defaultValue) values - def.key else values + (def.key to value))

    when (def.type) {
        SourceFilterType.HEADER -> Text(
            text = def.title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 8.dp),
        )

        SourceFilterType.SEPARATOR -> HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )

        SourceFilterType.SELECT -> AppCollapsibleFilterSection(title = def.title, onLongClick = { set(def.defaultValue) }) {
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                def.options.forEachIndexed { index, option ->
                    FilterChip(
                        selected = current() == index.toString(),
                        onClick = { set(index.toString()) },
                        label = { Text(option) },
                    )
                }
            }
        }

        SourceFilterType.SORT -> AppCollapsibleFilterSection(title = def.title, onLongClick = { set(def.defaultValue) }) {
            val selectedIndex = current().substringBefore(':').toIntOrNull()
            val ascending = current().substringAfter(':', "1") == "1"
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                def.options.forEachIndexed { index, option ->
                    val selected = index == selectedIndex
                    FilterChip(
                        selected = selected,
                        // Tapping the selected option flips its direction, like the sort row in Aniyomi.
                        onClick = { set("$index:${if (selected && ascending) 0 else 1}") },
                        label = { Text(option) },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    imageVector = if (ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                                    contentDescription = null,
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }

        SourceFilterType.CHECKBOX -> Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(def.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Switch(checked = current().toBoolean(), onCheckedChange = { set(it.toString()) })
        }

        SourceFilterType.TRISTATE -> TriStateChip(def, current(), ::set)

        SourceFilterType.TEXT -> OutlinedTextField(
            value = current(),
            onValueChange = ::set,
            label = { Text(def.title) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        )

        SourceFilterType.GROUP -> AppCollapsibleFilterSection(title = def.title, onLongClick = {
            onValuesChange(values - def.children.flatMap(::descendantKeys).toSet())
        }) {
            // Genre style groups are many small toggles: show them as one wrapping row of chips.
            val compact = def.children.all {
                it.type == SourceFilterType.CHECKBOX || it.type == SourceFilterType.TRISTATE
            }
            if (compact) {
                FlowRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    def.children.forEach { child ->
                        val value = values[child.key] ?: child.defaultValue
                        if (child.type == SourceFilterType.CHECKBOX) {
                            FilterChip(
                                selected = value.toBoolean(),
                                onClick = {
                                    val next = (!value.toBoolean()).toString()
                                    onValuesChange(
                                        if (next == child.defaultValue) values - child.key else values + (child.key to next),
                                    )
                                },
                                label = { Text(child.title) },
                            )
                        } else {
                            TriStateChip(child, value) { next ->
                                onValuesChange(
                                    if (next == child.defaultValue) values - child.key else values + (child.key to next),
                                )
                            }
                        }
                    }
                }
            } else {
                Column { SourceFilterControls(def.children, values, onValuesChange) }
            }
        }
    }
}

/** Ignored, then included, then excluded, then ignored again on each tap. */
@Composable
private fun TriStateChip(def: SourceFilterDef, value: String, onChange: (String) -> Unit) {
    val state = value.toIntOrNull() ?: 0
    FilterChip(
        selected = state != 0,
        onClick = { onChange(((state + 1) % 3).toString()) },
        label = { Text(def.title) },
        leadingIcon = when (state) {
            1 -> ({ Icon(imageVector = Icons.Filled.Check, contentDescription = null) })
            2 -> ({ Icon(imageVector = Icons.Filled.Close, contentDescription = null) })
            else -> null
        },
    )
}

private fun descendantKeys(def: SourceFilterDef): List<String> =
    listOf(def.key) + def.children.flatMap(::descendantKeys)

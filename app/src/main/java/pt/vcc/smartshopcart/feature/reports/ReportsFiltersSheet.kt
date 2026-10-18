package pt.vcc.smartshopcart.feature.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pt.vcc.smartshopcart.R
import pt.vcc.smartshopcart.domain.reports.ReportData
import pt.vcc.smartshopcart.domain.reports.ReportOption
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsFiltersSheet(
    report: ReportData,
    onDismiss: () -> Unit,
    onToggleCategory: (String) -> Unit,
    onToggleEstablishment: (String) -> Unit,
    onToggleProduct: (String) -> Unit,
    onClear: () -> Unit,
    onPickRange: () -> Unit,
) {
    val filter = report.filter
    val options = report.options
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.reports_filters),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                TextButton(onClick = onClear, enabled = filter.hasDimensionFilters) {
                    Text(stringResource(R.string.reports_clear_filters))
                }
            }
            Text(ReportsText.range(filter.startDate, filter.endDateInclusive), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onPickRange) { Text(stringResource(R.string.reports_custom_range_pick)) }

            OptionGroup(
                title = stringResource(R.string.reports_section_categories),
                options = withSelected(options.categories, filter.categoryIds, report),
                selected = filter.categoryIds,
                onToggle = onToggleCategory,
            )
            OptionGroup(
                title = stringResource(R.string.reports_section_establishments),
                options = withSelected(options.establishments, filter.establishmentIds, report),
                selected = filter.establishmentIds,
                onToggle = onToggleEstablishment,
            )
            OptionGroup(
                title = stringResource(R.string.reports_section_products),
                hint = stringResource(R.string.reports_products_hint),
                options = withSelected(options.products, filter.productIds, report),
                selected = filter.productIds,
                onToggle = onToggleProduct,
            )
        }
    }
}

/** Garante que as opções selecionadas continuam visíveis mesmo que já não estejam entre as sugeridas. */
private fun withSelected(options: List<ReportOption>, selected: Set<String>, report: ReportData): List<ReportOption> {
    val missing = selected - options.map { it.id }.toSet()
    if (missing.isEmpty()) return options
    val labels = report.rows.flatMap {
        listOf(it.categoryKey to it.categoryName, it.establishmentKey to it.establishmentName, it.productKey to it.productName)
    }.toMap()
    return options + missing.map { ReportOption(it, labels[it] ?: it, 0) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionGroup(
    title: String,
    options: List<ReportOption>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    hint: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (options.isEmpty()) {
            Text(stringResource(R.string.reports_filter_none), style = MaterialTheme.typography.bodySmall)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    FilterChip(
                        selected = option.id in selected,
                        onClick = { onToggle(option.id) },
                        label = { Text(option.label) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDateRangeDialog(
    initialStart: LocalDate?,
    initialEnd: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStart?.toUtcMillis(),
        initialSelectedEndDateMillis = initialEnd?.toUtcMillis(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            val start = state.selectedStartDateMillis
            val end = state.selectedEndDateMillis ?: start
            TextButton(
                onClick = { if (start != null && end != null) onConfirm(start.toUtcDate(), end.toUtcDate()) },
                enabled = start != null,
            ) { Text(stringResource(R.string.reports_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DateRangePicker(
            state = state,
            title = { Text(stringResource(R.string.reports_custom_range_title), Modifier.padding(start = 24.dp, top = 16.dp)) },
            modifier = Modifier.weight(1f),
        )
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

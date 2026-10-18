package pt.vcc.smartshopcart.feature.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pt.vcc.smartshopcart.R
import pt.vcc.smartshopcart.data.reports.export.ExportFormat
import pt.vcc.smartshopcart.data.reports.export.ExportGranularity
import pt.vcc.smartshopcart.data.reports.export.ExportOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportReportSheet(
    formats: List<ExportFormat>,
    defaultName: (ExportFormat) -> String,
    exporting: Boolean,
    onSave: (ExportFormat, ExportOptions, String) -> Unit,
    onShare: (ExportFormat, ExportOptions, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var format by rememberSaveable { mutableStateOf(formats.first()) }
    var granularity by rememberSaveable { mutableStateOf(ExportGranularity.ITEMS) }
    var name by rememberSaveable(format) { mutableStateOf(defaultName(format)) }
    val options = ExportOptions(granularity)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.reports_export_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            SectionTitle(stringResource(R.string.reports_export_format))
            Column(Modifier.selectableGroup()) {
                formats.forEach { option ->
                    RadioRow(stringResource(formatLabel(option)), selected = format == option) { format = option }
                }
            }
            if (format != ExportFormat.PDF) {
                SectionTitle(stringResource(R.string.reports_export_granularity))
                Column(Modifier.selectableGroup()) {
                    RadioRow(stringResource(R.string.reports_export_items), granularity == ExportGranularity.ITEMS) {
                        granularity = ExportGranularity.ITEMS
                    }
                    RadioRow(stringResource(R.string.reports_export_transactions), granularity == ExportGranularity.TRANSACTIONS) {
                        granularity = ExportGranularity.TRANSACTIONS
                    }
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.reports_export_file_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.reports_export_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (exporting) {
                Text(stringResource(R.string.reports_export_in_progress), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                OutlinedButton(
                    onClick = { onShare(format, options, name) },
                    enabled = !exporting,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.reports_export_share)) }
                Button(
                    onClick = { onSave(format, options, name) },
                    enabled = !exporting,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.reports_export_save)) }
            }
        }
    }
}

private fun formatLabel(format: ExportFormat): Int = when (format) {
    ExportFormat.CSV -> R.string.reports_export_format_csv
    ExportFormat.JSON -> R.string.reports_export_format_json
    ExportFormat.PDF -> R.string.reports_export_format_pdf
    else -> R.string.reports_export_format_csv
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
    }
}

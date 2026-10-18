package pt.vcc.smartshopcart.feature.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.vcc.smartshopcart.R
import pt.vcc.smartshopcart.core.money.MoneyFormatter
import pt.vcc.smartshopcart.domain.reports.ReportBreakdown
import pt.vcc.smartshopcart.domain.reports.ReportFilter
import pt.vcc.smartshopcart.domain.reports.ReportMode
import pt.vcc.smartshopcart.domain.reports.ReportPeriodPreset
import pt.vcc.smartshopcart.feature.reports.charts.ChartGroupValue
import pt.vcc.smartshopcart.feature.reports.charts.ChartValue

@Composable
internal fun PeriodChips(filter: ReportFilter?, onPreset: (ReportPeriodPreset) -> Unit, onCustom: () -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(ReportPeriodPreset.entries, key = { it.name }) { preset ->
            FilterChip(
                selected = filter?.preset == preset,
                onClick = { if (preset == ReportPeriodPreset.CUSTOM) onCustom() else onPreset(preset) },
                label = { Text(stringResource(ReportsText.preset(preset))) },
            )
        }
    }
}

@Composable
internal fun ModeChips(filter: ReportFilter?, onMode: (ReportMode?) -> Unit) {
    val selected: ReportMode? = filter?.modes?.singleOrNull()
    val all = filter == null || filter.modes.size == ReportMode.entries.size
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(selected = all, onClick = { onMode(null) }, label = { Text(stringResource(ReportsText.mode(null))) })
        }
        items(ReportMode.entries, key = { it.name }) { mode ->
            FilterChip(
                selected = !all && selected == mode,
                onClick = { onMode(mode) },
                label = { Text(stringResource(ReportsText.mode(mode))) },
            )
        }
    }
}

@Composable
internal fun ReportSection(
    title: String,
    modifier: Modifier = Modifier,
    onDetail: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (onDetail != null) {
                    TextButton(onClick = onDetail) { Text(stringResource(R.string.reports_see_detail)) }
                }
            }
            content()
        }
    }
}

@Composable
internal fun KpiCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier.semantics(mergeDescendants = true) {},
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun WarningCard(text: String) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
    }
}

internal fun ReportView.timelineValues(): List<ChartValue> =
    timeline.map { ChartValue(ReportsText.bucket(it.start, granularity), it.amountCents) }

@Composable
internal fun ReportView.timelineDescription(): String {
    val filter = report.filter
    val peak = timeline.maxByOrNull { it.amountCents }
    return stringResource(
        R.string.reports_chart_timeline_desc,
        ReportsText.date(filter.startDate),
        ReportsText.date(filter.endDateInclusive),
        MoneyFormatter.format(peak?.amountCents ?: 0),
        peak?.let { ReportsText.bucket(it.start, granularity) } ?: "—",
    )
}

internal fun List<ReportBreakdown>.toChartValues(): List<ChartValue> = map { ChartValue(it.label, it.amountCents) }

@Composable
internal fun categoriesDescription(categories: List<ReportBreakdown>): String = stringResource(
    R.string.reports_chart_categories_desc,
    categories.joinToString("; ") { "${it.label} ${MoneyFormatter.format(it.amountCents)} (${ReportsText.percent(it.share)})" },
)

internal fun ReportView.budgetValues(limit: Int = Int.MAX_VALUE): List<ChartGroupValue> =
    report.budgets.takeLast(limit).map { ChartGroupValue(it.label, it.budgetCents, it.spentCents) }

@Composable
internal fun budgetDescription(values: List<ChartGroupValue>): String = stringResource(
    R.string.reports_chart_budget_desc,
    values.joinToString("; ") {
        "${it.label}: ${MoneyFormatter.format(it.first)} / ${MoneyFormatter.format(it.second)}"
    },
)

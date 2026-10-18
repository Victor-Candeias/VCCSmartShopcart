package pt.vcc.smartshopcart.feature.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.smartshopcart.R
import pt.vcc.smartshopcart.core.common.DateTimeText
import pt.vcc.smartshopcart.core.money.MoneyFormatter
import pt.vcc.smartshopcart.core.money.QuantityParser
import pt.vcc.smartshopcart.domain.reports.ReportBreakdown
import pt.vcc.smartshopcart.domain.reports.ReportSeries
import pt.vcc.smartshopcart.domain.reports.TimeGranularity
import pt.vcc.smartshopcart.feature.reports.charts.ChartLegend
import pt.vcc.smartshopcart.feature.reports.charts.ChartValue
import pt.vcc.smartshopcart.feature.reports.charts.ColorDot
import pt.vcc.smartshopcart.feature.reports.charts.DonutChart
import pt.vcc.smartshopcart.feature.reports.charts.GroupedBarChart
import pt.vcc.smartshopcart.feature.reports.charts.HorizontalBarChart
import pt.vcc.smartshopcart.feature.reports.charts.LineChart
import pt.vcc.smartshopcart.feature.reports.charts.chartColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailScreen(
    kind: ReportDetailKind,
    onBack: () -> Unit,
    viewModel: ReportsViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(kind.title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (val current = state) {
                ReportsUiState.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) { CircularProgressIndicator() }
                }
                is ReportsUiState.Error -> item {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.reports_error, current.message), textAlign = TextAlign.Center)
                        Button(onClick = viewModel::retry) { Text(stringResource(R.string.action_retry)) }
                    }
                }
                is ReportsUiState.Ready -> {
                    val view = current.view
                    item(key = "period") {
                        Text(
                            ReportsText.range(view.report.filter.startDate, view.report.filter.endDateInclusive),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    if (view.report.isEmpty) {
                        item {
                            Text(
                                stringResource(R.string.reports_empty),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                            )
                        }
                    } else {
                        when (kind) {
                            ReportDetailKind.TIMELINE -> timelineDetail(view, viewModel::selectGranularity)
                            ReportDetailKind.CATEGORIES -> categoriesDetail(view)
                            ReportDetailKind.ESTABLISHMENTS -> breakdownDetail(view.establishments)
                            ReportDetailKind.PRODUCTS -> productsDetail(view)
                            ReportDetailKind.BUDGET -> budgetDetail(view)
                            ReportDetailKind.TRANSACTIONS -> transactionsDetail(view)
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.timelineDetail(view: ReportView, onGranularity: (TimeGranularity) -> Unit) {
    item(key = "granularity") {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TimeGranularity.entries, key = { it.name }) { granularity ->
                FilterChip(
                    selected = view.granularity == granularity,
                    onClick = { onGranularity(granularity) },
                    label = { Text(stringResource(ReportsText.granularity(granularity))) },
                )
            }
        }
    }
    item(key = "chart") {
        Box(Modifier.padding(16.dp)) { LineChart(view.timelineValues(), view.timelineDescription()) }
    }
    items(view.timeline.filter { it.amountCents != 0L || it.transactionCount > 0 }, key = { it.start.toString() }) { point ->
        val label = when (view.granularity) {
            TimeGranularity.WEEK -> stringResource(R.string.reports_week_of, ReportsText.date(point.start))
            TimeGranularity.MONTH -> ReportsText.bucket(point.start, TimeGranularity.MONTH)
            TimeGranularity.DAY -> ReportsText.date(point.start)
        }
        TableRow(
            label = label,
            secondary = pluralStringResource(R.plurals.reports_transactions_count, point.transactionCount, point.transactionCount),
            value = MoneyFormatter.format(point.amountCents),
        )
    }
}

private fun LazyListScope.categoriesDetail(view: ReportView) {
    item(key = "chart") {
        val slices = ReportSeries.collapse(view.categories)
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            DonutChart(slices.toChartValues(), categoriesDescription(slices), MoneyFormatter.format(view.report.summary.totalCents))
            ChartLegend(slices.map { it.label }, Modifier.padding(top = 12.dp))
        }
    }
    breakdownRows(view.categories, showColors = true)
}

private fun LazyListScope.breakdownDetail(values: List<ReportBreakdown>) {
    item(key = "chart") {
        Box(Modifier.padding(16.dp)) { HorizontalBarChart(values.take(10).toChartValues(), MoneyFormatter::format) }
    }
    breakdownRows(values, showColors = false)
}

private fun LazyListScope.breakdownRows(values: List<ReportBreakdown>, showColors: Boolean) {
    val colored = ReportSeries.collapse(values).map { it.id }
    items(values, key = { it.id }) { value ->
        val colorIndex = colored.indexOf(value.id).takeIf { showColors && it >= 0 }
        TableRow(
            label = value.label,
            secondary = stringResource(
                R.string.reports_share_line,
                ReportsText.percent(value.share),
                pluralStringResource(R.plurals.reports_transactions_count, value.transactionCount, value.transactionCount),
            ),
            value = MoneyFormatter.format(value.amountCents),
            leading = colorIndex?.let { { ColorDot(chartColor(it)) } },
        )
    }
}

private fun LazyListScope.productsDetail(view: ReportView) {
    item(key = "chart") {
        Box(Modifier.padding(16.dp)) {
            HorizontalBarChart(view.products.take(10).map { ChartValue(it.label, it.amountCents) }, MoneyFormatter::format)
        }
    }
    items(view.products, key = { it.id }) { product ->
        TableRow(
            label = product.label,
            secondary = stringResource(
                R.string.reports_share_line,
                "${QuantityParser.format(product.quantity)} ${product.unit}",
                pluralStringResource(R.plurals.reports_frequency, product.frequency, product.frequency),
            ),
            value = MoneyFormatter.format(product.amountCents),
        )
    }
}

private fun LazyListScope.budgetDetail(view: ReportView) {
    val filter = view.report.filter
    if (filter.categoryIds.isNotEmpty() || filter.productIds.isNotEmpty()) {
        item { InfoText(stringResource(R.string.reports_budget_filtered)) }
        return
    }
    if (view.report.budgets.isEmpty()) {
        item { InfoText(stringResource(R.string.reports_no_budgets)) }
        return
    }
    item(key = "chart") {
        val values = view.budgetValues(limit = 12)
        Box(Modifier.padding(16.dp)) {
            GroupedBarChart(values, stringResource(R.string.reports_budget), stringResource(R.string.reports_spent), budgetDescription(values))
        }
    }
    items(view.report.budgets.asReversed(), key = { it.transactionId }) { budget ->
        val difference = MoneyFormatter.format(kotlin.math.abs(budget.differenceCents))
        TableRow(
            label = budget.label,
            secondary = DateTimeText.date(budget.occurredAt.toEpochMilli(), view.report.zoneId) + " · " +
                MoneyFormatter.format(budget.spentCents) + " / " + MoneyFormatter.format(budget.budgetCents),
            value = stringResource(if (budget.overBudget) R.string.reports_budget_over else R.string.reports_budget_under, difference),
            valueColorError = budget.overBudget,
        )
    }
}

private fun LazyListScope.transactionsDetail(view: ReportView) {
    items(view.transactions, key = { it.transactionId }) { transaction ->
        TableRow(
            label = transaction.establishmentName ?: stringResource(ReportsText.mode(transaction.mode)),
            secondary = DateTimeText.dateTime(transaction.occurredAt.toEpochMilli(), view.report.zoneId) + " · " +
                stringResource(ReportsText.mode(transaction.mode)) + " · " +
                pluralStringResource(R.plurals.reports_items_count, transaction.itemCount, transaction.itemCount),
            value = MoneyFormatter.format(transaction.totalCents),
        )
    }
}

@Composable
private fun InfoText(text: String) {
    Text(text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
}

@Composable
private fun TableRow(
    label: String,
    secondary: String,
    value: String,
    leading: (@Composable () -> Unit)? = null,
    valueColorError: Boolean = false,
) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(secondary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                color = if (valueColorError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
    }
}

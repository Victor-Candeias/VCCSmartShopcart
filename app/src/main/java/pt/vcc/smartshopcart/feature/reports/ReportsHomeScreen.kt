package pt.vcc.smartshopcart.feature.reports

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import pt.vcc.smartshopcart.data.reports.export.ExportCoordinator
import pt.vcc.smartshopcart.data.reports.export.ExportFormat
import pt.vcc.smartshopcart.data.reports.export.ExportGranularity
import pt.vcc.smartshopcart.data.reports.export.ExportOptions
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.smartshopcart.R
import pt.vcc.smartshopcart.core.money.MoneyFormatter
import pt.vcc.smartshopcart.domain.reports.ReportFilter
import pt.vcc.smartshopcart.domain.reports.ReportSeries
import pt.vcc.smartshopcart.feature.reports.charts.ChartLegend
import pt.vcc.smartshopcart.feature.reports.charts.ChartValue
import pt.vcc.smartshopcart.feature.reports.charts.DonutChart
import pt.vcc.smartshopcart.feature.reports.charts.GroupedBarChart
import pt.vcc.smartshopcart.feature.reports.charts.HorizontalBarChart
import pt.vcc.smartshopcart.feature.reports.charts.LineChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsHomeScreen(
    onOpenDetail: (ReportDetailKind) -> Unit,
    viewModel: ReportsViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val exporting by viewModel.exporting.collectAsStateWithLifecycle()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showRangePicker by rememberSaveable { mutableStateOf(false) }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var pendingFormat by rememberSaveable { mutableStateOf<ExportFormat?>(null) }
    var pendingGranularity by rememberSaveable { mutableStateOf(ExportGranularity.ITEMS) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val doneText = stringResource(R.string.reports_export_done)
    val failedText = stringResource(R.string.reports_export_failed)
    val noAppText = stringResource(R.string.reports_export_no_app)
    val shareTitle = stringResource(R.string.reports_export_share)

    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        val format = pendingFormat
        if (result.resultCode == Activity.RESULT_OK && uri != null && format != null) {
            viewModel.exportToUri(uri, format, ExportOptions(pendingGranularity))
        }
        pendingFormat = null
    }

    LaunchedEffect(viewModel) {
        viewModel.exportEvents.collect { event ->
            when (event) {
                ExportEvent.Saved -> {
                    showExport = false
                    snackbar.showSnackbar(doneText)
                }
                is ExportEvent.Share -> {
                    showExport = false
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = event.mimeType
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        clipData = ClipData.newRawUri("", event.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        context.startActivity(Intent.createChooser(send, shareTitle))
                    } catch (_: ActivityNotFoundException) {
                        snackbar.showSnackbar(noAppText)
                    }
                }
                is ExportEvent.Failed -> snackbar.showSnackbar(String.format(failedText, event.message))
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reports_title)) },
                actions = {
                    IconButton(onClick = { showFilters = true }, enabled = state is ReportsUiState.Ready) {
                        Icon(painterResource(R.drawable.ic_filter), contentDescription = stringResource(R.string.reports_filters))
                    }
                    val canExport = (state as? ReportsUiState.Ready)?.view?.report?.isEmpty == false
                    IconButton(onClick = { showExport = true }, enabled = canExport) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.reports_export))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "chips") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PeriodChips(filter, onPreset = viewModel::selectPreset, onCustom = { showRangePicker = true })
                    ModeChips(filter, onMode = viewModel::selectMode)
                }
            }
            filter?.let { current -> item(key = "period") { PeriodLine(current, viewModel::clearDimensionFilters) } }
            when (val current = state) {
                ReportsUiState.Loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) { CircularProgressIndicator() }
                }
                is ReportsUiState.Error -> item(key = "error") {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.reports_error, current.message), textAlign = TextAlign.Center)
                        Button(onClick = viewModel::retry) { Text(stringResource(R.string.action_retry)) }
                    }
                }
                is ReportsUiState.Ready -> readyContent(current.view, onOpenDetail)
            }
        }
    }

    val current = state
    if (showFilters && current is ReportsUiState.Ready) {
        ReportsFiltersSheet(
            report = current.view.report,
            onDismiss = { showFilters = false },
            onToggleCategory = viewModel::toggleCategory,
            onToggleEstablishment = viewModel::toggleEstablishment,
            onToggleProduct = viewModel::toggleProduct,
            onClear = viewModel::clearDimensionFilters,
            onPickRange = { showRangePicker = true },
        )
    }
    if (showExport && current is ReportsUiState.Ready) {
        ExportReportSheet(
            formats = viewModel.exportFormats,
            defaultName = { viewModel.defaultExportName(it).orEmpty() },
            exporting = exporting,
            onSave = { format, options, name ->
                pendingFormat = format
                pendingGranularity = options.granularity
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = format.mimeType
                    putExtra(Intent.EXTRA_TITLE, ExportCoordinator.sanitizeFileName(name, format))
                }
                try {
                    createDocument.launch(intent)
                } catch (_: ActivityNotFoundException) {
                    pendingFormat = null
                    Toast.makeText(context, noAppText, Toast.LENGTH_LONG).show()
                }
            },
            onShare = viewModel::exportForShare,
            onDismiss = { showExport = false },
        )
    }
    if (showRangePicker) {
        ReportDateRangeDialog(
            initialStart = filter?.startDate,
            initialEnd = filter?.endDateInclusive,
            onDismiss = { showRangePicker = false },
            onConfirm = { start, end ->
                showRangePicker = false
                viewModel.selectCustomRange(start, end)
            },
        )
    }
}

@Composable
private fun PeriodLine(filter: ReportFilter, onClear: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            ReportsText.range(filter.startDate, filter.endDateInclusive),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        val active = filter.categoryIds.size + filter.establishmentIds.size + filter.productIds.size
        if (active > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(pluralStringResource(R.plurals.reports_active_filters, active, active)) })
                TextButton(onClick = onClear) { Text(stringResource(R.string.reports_clear_filters)) }
            }
        }
    }
}

private fun LazyListScope.readyContent(view: ReportView, onOpenDetail: (ReportDetailKind) -> Unit) {
    val report = view.report
    if (report.dataQuality.hasIssues) {
        item(key = "quality") {
            WarningCard(
                stringResource(
                    R.string.reports_data_warning,
                    report.dataQuality.transactionsWithoutItems,
                    report.dataQuality.totalMismatches,
                ),
            )
        }
    }
    if (report.isEmpty) {
        item(key = "empty") {
            Text(
                stringResource(if (report.filter.hasDimensionFilters) R.string.reports_empty_filtered else R.string.reports_empty),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(32.dp),
            )
        }
        return
    }
    item(key = "kpis") { Kpis(view) }
    item(key = "timeline") {
        ReportSection(stringResource(R.string.reports_section_timeline), onDetail = { onOpenDetail(ReportDetailKind.TIMELINE) }) {
            LineChart(view.timelineValues(), view.timelineDescription())
        }
    }
    item(key = "categories") {
        ReportSection(stringResource(R.string.reports_section_categories), onDetail = { onOpenDetail(ReportDetailKind.CATEGORIES) }) {
            val slices = ReportSeries.collapse(view.categories)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                DonutChart(slices.toChartValues(), categoriesDescription(slices), MoneyFormatter.format(report.summary.totalCents))
                ChartLegend(slices.map { "${it.label} ${ReportsText.percent(it.share)}" }, Modifier.padding(top = 12.dp))
            }
        }
    }
    item(key = "establishments") {
        ReportSection(
            stringResource(R.string.reports_section_establishments),
            onDetail = { onOpenDetail(ReportDetailKind.ESTABLISHMENTS) },
        ) {
            HorizontalBarChart(view.establishments.take(5).toChartValues(), MoneyFormatter::format)
        }
    }
    item(key = "products") {
        ReportSection(stringResource(R.string.reports_section_products), onDetail = { onOpenDetail(ReportDetailKind.PRODUCTS) }) {
            HorizontalBarChart(view.products.take(5).map { ChartValue(it.label, it.amountCents) }, MoneyFormatter::format)
        }
    }
    if (report.budgets.isNotEmpty()) {
        item(key = "budget") {
            ReportSection(stringResource(R.string.reports_section_budget), onDetail = { onOpenDetail(ReportDetailKind.BUDGET) }) {
                val values = view.budgetValues(limit = 6)
                GroupedBarChart(
                    values,
                    stringResource(R.string.reports_budget),
                    stringResource(R.string.reports_spent),
                    budgetDescription(values),
                )
            }
        }
    }
    item(key = "transactions") {
        ReportSection(
            stringResource(R.string.reports_section_transactions),
            onDetail = { onOpenDetail(ReportDetailKind.TRANSACTIONS) },
        ) {
            val count = report.summary.transactionCount
            Text(pluralStringResource(R.plurals.reports_transactions_count, count, count))
        }
    }
}

@Composable
private fun Kpis(view: ReportView) {
    val summary = view.report.summary
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiCard(stringResource(R.string.reports_kpi_total), MoneyFormatter.format(summary.totalCents), Modifier.weight(1f))
            KpiCard(stringResource(R.string.reports_kpi_transactions), summary.transactionCount.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiCard(stringResource(R.string.reports_kpi_average), MoneyFormatter.format(summary.averageTransactionCents), Modifier.weight(1f))
            KpiCard(stringResource(R.string.reports_kpi_items), summary.itemCount.toString(), Modifier.weight(1f))
        }
        view.report.comparison?.let { comparison ->
            val previous = ReportsText.range(comparison.previousStartDate, comparison.previousEndDateInclusive)
            val percent = ReportsText.signedPercent(comparison)
            val text = if (percent == null) {
                stringResource(R.string.reports_comparison_no_previous, previous)
            } else {
                val difference = comparison.differenceCents
                val amount = (if (difference > 0) "+" else "") + MoneyFormatter.format(difference)
                stringResource(R.string.reports_comparison, amount, percent, previous)
            }
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (comparison.currentPeriodIncomplete) {
                Text(
                    stringResource(R.string.reports_comparison_incomplete),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

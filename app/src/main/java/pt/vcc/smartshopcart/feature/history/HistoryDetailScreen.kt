package pt.vcc.smartshopcart.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.smartshopcart.R
import pt.vcc.smartshopcart.core.common.DateTimeText
import pt.vcc.smartshopcart.core.money.MoneyFormatter
import pt.vcc.smartshopcart.core.ui.components.BudgetBar
import pt.vcc.smartshopcart.core.ui.components.SessionItemRow
import pt.vcc.smartshopcart.core.ui.components.sessionItemTitle
import pt.vcc.smartshopcart.domain.usecase.StorePriceSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(
    onBack: () -> Unit,
    viewModel: HistoryDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val priceHistory by viewModel.priceHistory.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                HistoryDetailEvent.Deleted -> onBack()
                HistoryDetailEvent.DeleteFailed -> snackbar.showSnackbar(context.getString(R.string.history_delete_failed))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.history_detail_title))
                        state.session?.let {
                            Text(
                                state.storeName ?: stringResource(R.string.history_no_store),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.session != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.history_delete))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val session = state.session
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            session == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(stringResource(R.string.session_missing))
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    Column(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            stringResource(
                                R.string.history_completed_at,
                                DateTimeText.dateTime(session.completedAt ?: session.startedAt),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        state.listName?.let {
                            Text(stringResource(R.string.history_list_name, it), style = MaterialTheme.typography.bodyMedium)
                        }
                        state.budget?.let { BudgetBar(it, Modifier.padding(top = 4.dp)) }
                        Text(
                            stringResource(R.string.history_read_only) + " " + stringResource(R.string.history_price_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                }
                items(state.items, key = { it.id }) { item ->
                    SessionItemRow(
                        item = item,
                        onClick = if (item.barcode != null) ({ viewModel.onShowPrices(item) }) else null,
                        onRemove = null,
                        onClickLabel = stringResource(R.string.history_price_action),
                    )
                }
            }
        }
    }

    priceHistory?.let { history ->
        AlertDialog(
            onDismissRequest = viewModel::onHidePrices,
            title = { Text(stringResource(R.string.history_price_title, sessionItemTitle(history.item))) },
            text = {
                val prices = history.prices
                when {
                    prices == null -> CircularProgressIndicator()
                    prices.isEmpty() -> Text(stringResource(R.string.history_price_empty))
                    else -> Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) { prices.forEach { StorePriceRow(it) } }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::onHidePrices) { Text(stringResource(R.string.action_close)) }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = { Text(stringResource(R.string.history_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.onDelete()
                }) { Text(stringResource(R.string.history_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun StorePriceRow(summary: StorePriceSummary) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text(
            summary.storeName ?: stringResource(R.string.history_no_store),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            stringResource(
                R.string.history_price_last,
                MoneyFormatter.formatPerUnit(summary.last.priceCents, summary.unit),
                DateTimeText.date(summary.last.observedAt),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (summary.count > 1) {
            Text(
                stringResource(
                    R.string.history_price_range,
                    MoneyFormatter.formatPerUnit(summary.minCents, summary.unit),
                    MoneyFormatter.formatPerUnit(summary.maxCents, summary.unit),
                ) + " · " + pluralStringResource(R.plurals.history_price_count, summary.count, summary.count),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

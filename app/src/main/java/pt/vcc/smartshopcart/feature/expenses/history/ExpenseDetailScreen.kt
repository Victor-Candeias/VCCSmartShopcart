package pt.vcc.smartshopcart.feature.expenses.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.smartshopcart.R
import pt.vcc.smartshopcart.core.common.DateTimeText
import pt.vcc.smartshopcart.core.money.MoneyFormatter
import pt.vcc.smartshopcart.domain.model.expense.ExpenseItem
import pt.vcc.smartshopcart.feature.expenses.components.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailScreen(
    onBack: () -> Unit,
    onEdit: (expenseId: String) -> Unit,
    onRepeat: (expenseId: String) -> Unit,
    viewModel: ExpenseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ExpenseDetailEvent.Deleted -> onBack()
                ExpenseDetailEvent.DeleteFailed -> snackbar.showSnackbar(context.getString(R.string.expense_delete_failed))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.expense_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.details != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.expense_delete))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val details = state.details
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            details == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(stringResource(R.string.expense_missing))
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            details.establishment?.name ?: stringResource(R.string.expense_no_establishment),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(DateTimeText.dateTime(details.expense.occurredAt), style = MaterialTheme.typography.bodyMedium)
                        if (state.categories.isNotEmpty()) {
                            Text(state.categories.joinToString(" · ") { it.label() }, style = MaterialTheme.typography.bodySmall)
                        }
                        details.expense.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                    HorizontalDivider()
                }
                items(details.items, key = { it.id }) { ItemRow(it) }
                item {
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(16.dp).semantics(mergeDescendants = true) {}) {
                        Text(stringResource(R.string.expense_total), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        Text(
                            MoneyFormatter.format(details.expense.totalCents),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onEdit(viewModel.expenseId) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.expense_edit_action))
                        }
                        Button(onClick = { onRepeat(viewModel.expenseId) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.expense_repeat))
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        DeleteExpenseDialog(
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun ItemRow(item: ExpenseItem) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.nameSnapshot, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.expense_item_line, item.quantity, MoneyFormatter.format(item.unitPriceCents)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(MoneyFormatter.format(item.totalCents), style = MaterialTheme.typography.bodyLarge)
    }
}

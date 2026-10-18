package pt.vcc.smartshopcart.feature.expenses.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.smartshopcart.R
import pt.vcc.smartshopcart.core.common.DateTimeText
import pt.vcc.smartshopcart.core.money.MoneyFormatter
import pt.vcc.smartshopcart.domain.model.expense.ExpenseCategory
import pt.vcc.smartshopcart.domain.model.expense.ExpenseSummary
import pt.vcc.smartshopcart.feature.expenses.components.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseHistoryScreen(
    onBack: () -> Unit,
    onOpenExpense: (expenseId: String) -> Unit,
    onEditExpense: (expenseId: String) -> Unit,
    onRepeatExpense: (expenseId: String) -> Unit,
    viewModel: ExpenseHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.deleteFailed.collect { snackbar.showSnackbar(context.getString(R.string.expense_delete_failed)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.expense_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val current = state
        when {
            current == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            !current.hasAnyExpense -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), Alignment.Center) {
                Text(stringResource(R.string.expense_history_empty), textAlign = TextAlign.Center)
            }
            else -> {
                val categoriesById = current.categories.associateBy { it.id }
                val byDay = current.expenses.groupBy { DateTimeText.date(it.expense.occurredAt) }
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Filters(
                            categories = current.categories,
                            selectedCategory = current.categoryId,
                            onCategory = viewModel::selectCategory,
                            establishments = current.establishments,
                            selectedEstablishment = current.establishment,
                            onEstablishment = viewModel::selectEstablishment,
                        )
                        Text(
                            stringResource(R.string.expense_total_value, MoneyFormatter.format(current.totalCents)),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    if (current.expenses.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.expense_history_filtered_empty),
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    byDay.forEach { (day, expenses) ->
                        item(key = "day-$day") {
                            Text(
                                day,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).semantics { heading() },
                            )
                        }
                        items(expenses, key = { it.expense.id }) { summary ->
                            ExpenseCard(
                                summary = summary,
                                categories = summary.categoryIds.mapNotNull(categoriesById::get).sortedBy { it.sortOrder },
                                onOpen = { onOpenExpense(summary.expense.id) },
                                onEdit = { onEditExpense(summary.expense.id) },
                                onRepeat = { onRepeatExpense(summary.expense.id) },
                                onDelete = { deleting = summary.expense.id },
                            )
                        }
                    }
                }
            }
        }
    }

    deleting?.let { id ->
        DeleteExpenseDialog(
            onConfirm = {
                deleting = null
                viewModel.delete(id)
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun Filters(
    categories: List<ExpenseCategory>,
    selectedCategory: String?,
    onCategory: (String?) -> Unit,
    establishments: List<String>,
    selectedEstablishment: String?,
    onEstablishment: (String?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onCategory(null) },
                label = { Text(stringResource(R.string.expense_category_all)) },
            )
        }
        items(categories, key = { it.id }) { category ->
            FilterChip(
                selected = selectedCategory == category.id,
                onClick = { onCategory(if (selectedCategory == category.id) null else category.id) },
                label = { Text(category.label()) },
            )
        }
    }
    if (establishments.isNotEmpty()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = selectedEstablishment == null,
                    onClick = { onEstablishment(null) },
                    label = { Text(stringResource(R.string.expense_establishment_all)) },
                )
            }
            items(establishments, key = { it }) { name ->
                FilterChip(
                    selected = selectedEstablishment == name,
                    onClick = { onEstablishment(if (selectedEstablishment == name) null else name) },
                    label = { Text(name) },
                )
            }
        }
    }
}

@Composable
private fun ExpenseCard(
    summary: ExpenseSummary,
    categories: List<ExpenseCategory>,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRepeat: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val place = summary.establishmentName ?: stringResource(R.string.expense_no_establishment)
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(place, style = MaterialTheme.typography.titleMedium)
                Text(DateTimeText.dateTime(summary.expense.occurredAt), style = MaterialTheme.typography.bodyMedium)
                val count = pluralStringResource(R.plurals.items_count, summary.itemCount, summary.itemCount)
                val labels = categories.map { it.label() }
                Text((labels + count).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                MoneyFormatter.format(summary.expense.totalCents),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp),
            )
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.expense_actions_for, place))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.expense_view)) }, onClick = { menu = false; onOpen() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.expense_edit_action)) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.expense_repeat)) }, onClick = { menu = false; onRepeat() })
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.expense_delete), color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
internal fun DeleteExpenseDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.expense_delete_title)) },
        text = { Text(stringResource(R.string.expense_delete_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.expense_delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

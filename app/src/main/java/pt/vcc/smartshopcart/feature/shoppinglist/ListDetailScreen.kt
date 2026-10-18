package pt.vcc.smartshopcart.feature.shoppinglist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.smartshopcart.R
import pt.vcc.smartshopcart.core.money.MoneyFormatter
import pt.vcc.smartshopcart.core.money.QuantityParser
import pt.vcc.smartshopcart.core.ui.components.ListDialog
import pt.vcc.smartshopcart.core.ui.components.ListItemDialog
import pt.vcc.smartshopcart.core.ui.components.StartSessionDialog
import pt.vcc.smartshopcart.domain.model.MeasureUnit
import pt.vcc.smartshopcart.domain.model.SessionMode
import pt.vcc.smartshopcart.domain.model.ShoppingListItem
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(
    onBack: () -> Unit,
    onOpenList: (listId: String) -> Unit,
    onOpenSession: (sessionId: String, mode: SessionMode) -> Unit,
    viewModel: ListDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stores by viewModel.stores.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var editList by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var startDialog by rememberSaveable { mutableStateOf(false) }
    var itemDialogItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var itemDialogOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ListDetailEvent.OpenList -> onOpenList(event.listId)
                is ListDetailEvent.OpenSession -> onOpenSession(event.sessionId, event.mode)
                ListDetailEvent.ActiveSessionExists -> snackbar.showSnackbar(context.getString(R.string.session_active_exists_text))
                ListDetailEvent.Closed -> onBack()
            }
        }
    }

    val list = state.list
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(list?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (list != null) {
                        IconButton(onClick = { editList = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.list_edit))
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            val copyName = stringResource(R.string.list_copy_name, list.name)
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.list_duplicate)) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.duplicate(copyName)
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(if (list.archived) R.string.list_unarchive else R.string.list_archive))
                                },
                                onClick = {
                                    menuOpen = false
                                    viewModel.setArchived(!list.archived)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.list_delete)) },
                                onClick = {
                                    menuOpen = false
                                    confirmDelete = true
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            list == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(stringResource(R.string.list_missing))
            }
            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                ListSummaryHeader(state)
                HorizontalDivider()
                if (state.items.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), Alignment.Center) {
                        Text(stringResource(R.string.list_items_empty), textAlign = TextAlign.Center)
                    }
                } else {
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(state.items, key = { it.id }) { item ->
                            PlannedItemRow(
                                item = item,
                                onClick = {
                                    itemDialogItemId = item.id
                                    itemDialogOpen = true
                                },
                                onDelete = { viewModel.deleteItem(item.id) },
                            )
                        }
                    }
                }
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                itemDialogItemId = null
                                itemDialogOpen = true
                            },
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text(stringResource(R.string.list_item_add), Modifier.padding(start = 4.dp))
                        }
                        Button(
                            onClick = { startDialog = true },
                            enabled = !list.archived,
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) { Text(stringResource(R.string.list_start_shopping)) }
                    }
                }
            }
        }
    }

    if (editList && list != null) {
        ListDialog(
            title = stringResource(R.string.list_edit),
            initialName = list.name,
            initialBudgetCents = list.budgetCents,
            onDismiss = { editList = false },
            onConfirm = { name, budget ->
                editList = false
                viewModel.rename(name, budget)
            },
        )
    }
    if (itemDialogOpen) {
        val item = itemDialogItemId?.let { id -> state.items.firstOrNull { it.id == id } }
        ListItemDialog(
            title = stringResource(if (item == null) R.string.list_item_add else R.string.list_item_edit),
            initialName = item?.name.orEmpty(),
            initialQuantity = item?.plannedQuantity ?: BigDecimal.ONE,
            initialUnit = item?.unit ?: MeasureUnit.UN,
            initialEstimatedCents = item?.estimatedPriceCents,
            onDismiss = { itemDialogOpen = false },
            onConfirm = { name, quantity, unit, estimate ->
                itemDialogOpen = false
                viewModel.saveItem(item?.id, name, quantity, unit, estimate)
            },
        )
    }
    if (startDialog && list != null) {
        StartSessionDialog(
            title = stringResource(R.string.list_start_shopping),
            stores = stores,
            initialBudgetCents = list.budgetCents,
            onDismiss = { startDialog = false },
            onConfirm = { store, budget ->
                startDialog = false
                viewModel.startShopping(store, budget)
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.list_delete_title)) },
            text = { Text(stringResource(R.string.list_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.list_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ListSummaryHeader(state: ListDetailUiState) {
    val list = state.list ?: return
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (list.archived) {
            Text(stringResource(R.string.list_is_archived), color = MaterialTheme.colorScheme.error)
        }
        Text(
            stringResource(R.string.list_estimated_total, MoneyFormatter.format(state.estimatedTotalCents)),
            style = MaterialTheme.typography.titleMedium,
        )
        if (state.itemsWithoutEstimate > 0) {
            Text(stringResource(R.string.list_items_without_estimate, state.itemsWithoutEstimate), style = MaterialTheme.typography.bodySmall)
        }
        list.budgetCents?.let {
            Text(stringResource(R.string.list_budget_value, MoneyFormatter.format(it)), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PlannedItemRow(item: ShoppingListItem, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge)
            Text(plannedItemDetail(item), style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.list_item_delete, item.name))
        }
    }
}

fun plannedItemDetail(item: ShoppingListItem): String {
    val quantity = "${QuantityParser.format(item.plannedQuantity)} ${item.unit.symbol}"
    val estimate = item.estimatedPriceCents?.let { " · ~${MoneyFormatter.formatPerUnit(it, item.unit)}" }.orEmpty()
    return quantity + estimate
}

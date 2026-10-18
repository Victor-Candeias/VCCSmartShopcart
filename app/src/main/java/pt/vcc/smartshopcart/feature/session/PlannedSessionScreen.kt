package pt.vcc.smartshopcart.feature.session

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pt.vcc.smartshopcart.R
import pt.vcc.smartshopcart.core.money.MoneyFormatter
import pt.vcc.smartshopcart.core.ui.components.AddPriceDialog
import pt.vcc.smartshopcart.core.ui.components.BudgetBar
import pt.vcc.smartshopcart.core.ui.components.SessionItemRow
import pt.vcc.smartshopcart.domain.model.MeasureUnit
import pt.vcc.smartshopcart.domain.model.ShoppingListItem
import pt.vcc.smartshopcart.domain.model.ShoppingSessionItem
import pt.vcc.smartshopcart.feature.shoppinglist.plannedItemDetail
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannedSessionScreen(
    onBack: () -> Unit,
    onEditItem: (itemId: String) -> Unit,
    onSessionClosed: () -> Unit,
    viewModel: PlannedSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var confirmComplete by rememberSaveable { mutableStateOf(false) }
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    var addingListItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var addingExtra by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PlannedSessionEvent.ItemRemoved -> scope.launch {
                    val result = snackbar.showSnackbar(
                        message = context.getString(R.string.item_removed),
                        actionLabel = context.getString(R.string.action_undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.onUndoRemove(event.item)
                }
                PlannedSessionEvent.SessionClosed -> onSessionClosed()
                PlannedSessionEvent.SessionNotActive -> scope.launch {
                    snackbar.showSnackbar(context.getString(R.string.session_not_active))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.listName ?: stringResource(R.string.home_lists))
                        state.storeName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.isActive) {
                        TextButton(onClick = { confirmComplete = true }) { Text(stringResource(R.string.action_complete)) }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_cancel_session)) },
                                onClick = {
                                    menuOpen = false
                                    confirmCancel = true
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.isActive) {
                ExtendedFloatingActionButton(
                    onClick = { addingExtra = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.planned_add_extra)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            state.missing -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(stringResource(R.string.session_missing))
            }
            else -> PlannedContent(
                state = state,
                onAddPlanned = { addingListItemId = it.id },
                onSkip = { viewModel.setSkipped(it.id, true) },
                onRestore = { viewModel.setSkipped(it.id, false) },
                onEditItem = onEditItem,
                onRemove = viewModel::onRemove,
                modifier = Modifier.padding(padding),
            )
        }
    }

    val addingItem = addingListItemId?.let { id -> state.progress.entries.firstOrNull { it.listItem.id == id }?.listItem }
    if (addingItem != null) {
        AddPriceDialog(
            title = addingItem.name,
            askName = false,
            initialQuantity = addingItem.plannedQuantity,
            unit = addingItem.unit,
            initialPriceCents = addingItem.estimatedPriceCents,
            onDismiss = { addingListItemId = null },
            onConfirm = { _, price, quantity ->
                addingListItemId = null
                viewModel.addPlanned(addingItem, price, quantity)
            },
        )
    }
    if (addingExtra) {
        AddPriceDialog(
            title = stringResource(R.string.planned_add_extra),
            askName = true,
            initialQuantity = BigDecimal.ONE,
            unit = MeasureUnit.UN,
            initialPriceCents = null,
            onDismiss = { addingExtra = false },
            onConfirm = { name, price, quantity ->
                addingExtra = false
                viewModel.addExtra(name, price, quantity)
            },
        )
    }
    if (confirmComplete) {
        val pending = state.progress.pending.size
        AlertDialog(
            onDismissRequest = { confirmComplete = false },
            title = { Text(stringResource(R.string.session_complete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            R.string.session_complete_text,
                            state.itemCount,
                            MoneyFormatter.format(state.budget.totalCents),
                        ),
                    )
                    if (pending > 0) Text(stringResource(R.string.planned_complete_pending, pending))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmComplete = false
                    viewModel.onComplete()
                }) { Text(stringResource(R.string.action_complete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmComplete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(stringResource(R.string.session_cancel_title)) },
            text = { Text(stringResource(R.string.session_cancel_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    viewModel.onCancelSession()
                }) { Text(stringResource(R.string.action_cancel_session)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
}

@Composable
private fun PlannedContent(
    state: PlannedSessionUiState,
    onAddPlanned: (ShoppingListItem) -> Unit,
    onSkip: (ShoppingListItem) -> Unit,
    onRestore: (ShoppingListItem) -> Unit,
    onEditItem: (String) -> Unit,
    onRemove: (ShoppingSessionItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = state.progress
    val active = state.isActive
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BudgetBar(state.budget)
            Text(
                stringResource(R.string.planned_progress, progress.resolvedCount, progress.totalCount),
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
        }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 96.dp)) {
            section(R.string.planned_section_pending, progress.pending.size)
            items(progress.pending, key = { "p-" + it.listItem.id }) { entry ->
                PendingRow(
                    item = entry.listItem,
                    onClick = if (active) ({ onAddPlanned(entry.listItem) }) else null,
                    onSkip = if (active) ({ onSkip(entry.listItem) }) else null,
                )
            }
            val inCart = progress.added.flatMap { it.sessionItems }
            section(R.string.planned_section_in_cart, inCart.size)
            items(inCart, key = { "c-" + it.id }) { item ->
                SessionItemRow(
                    item = item,
                    onClick = if (active) ({ onEditItem(item.id) }) else null,
                    onRemove = if (active) ({ onRemove(item) }) else null,
                )
            }
            if (progress.extras.isNotEmpty()) {
                section(R.string.planned_section_extras, progress.extras.size)
                items(progress.extras, key = { "e-" + it.id }) { item ->
                    SessionItemRow(
                        item = item,
                        onClick = if (active) ({ onEditItem(item.id) }) else null,
                        onRemove = if (active) ({ onRemove(item) }) else null,
                    )
                }
            }
            if (progress.skipped.isNotEmpty()) {
                section(R.string.planned_section_skipped, progress.skipped.size)
                items(progress.skipped, key = { "s-" + it.listItem.id }) { entry ->
                    SkippedRow(entry.listItem, onRestore = if (active) ({ onRestore(entry.listItem) }) else null)
                }
            }
        }
    }
}

private fun LazyListScope.section(titleRes: Int, count: Int) {
    item(key = "h-$titleRes") {
        Text(
            stringResource(titleRes, count),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun PendingRow(item: ShoppingListItem, onClick: (() -> Unit)?, onSkip: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge)
            Text(plannedItemDetail(item), style = MaterialTheme.typography.bodySmall)
        }
        if (onSkip != null) {
            TextButton(onClick = onSkip) { Text(stringResource(R.string.planned_skip)) }
        }
    }
}

@Composable
private fun SkippedRow(item: ShoppingListItem, onRestore: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (onRestore != null) {
            TextButton(onClick = onRestore) { Text(stringResource(R.string.planned_restore)) }
        }
    }
}

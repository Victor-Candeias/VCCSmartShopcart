package pt.vcc.smartshopcart.feature.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pt.vcc.smartshopcart.R
import pt.vcc.smartshopcart.core.money.MoneyFormatter
import pt.vcc.smartshopcart.core.ui.components.BudgetBar
import pt.vcc.smartshopcart.core.ui.components.NumericKeypad
import pt.vcc.smartshopcart.core.ui.components.SessionItemRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    onBack: () -> Unit,
    onScan: (sessionId: String) -> Unit,
    onEditItem: (itemId: String) -> Unit,
    onSessionClosed: () -> Unit,
    viewModel: CalculatorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var confirmComplete by rememberSaveable { mutableStateOf(false) }
    var confirmCancel by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CalculatorEvent.ItemRemoved -> scope.launch {
                    val result = snackbar.showSnackbar(
                        message = context.getString(R.string.item_removed),
                        actionLabel = context.getString(R.string.action_undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.onUndoRemove(event.item)
                }
                CalculatorEvent.SessionClosed -> onSessionClosed()
                CalculatorEvent.SessionNotActive -> scope.launch {
                    snackbar.showSnackbar(context.getString(R.string.session_not_active))
                }
                CalculatorEvent.StorageError -> scope.launch {
                    snackbar.showSnackbar(context.getString(R.string.error_storage))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.home_calculator))
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
                        IconButton(onClick = { onScan(viewModel.sessionId) }) {
                            Icon(painterResource(R.drawable.ic_barcode), contentDescription = stringResource(R.string.action_scan))
                        }
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
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            state.missing -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(stringResource(R.string.session_missing))
            }
            else -> CalculatorContent(state, viewModel, onEditItem, Modifier.padding(padding))
        }
    }

    if (confirmComplete) {
        AlertDialog(
            onDismissRequest = { confirmComplete = false },
            title = { Text(stringResource(R.string.session_complete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.session_complete_text,
                        state.items.size,
                        MoneyFormatter.format(state.budget.totalCents),
                    ),
                )
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
private fun CalculatorContent(
    state: CalculatorUiState,
    viewModel: CalculatorViewModel,
    onEditItem: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        BudgetBar(state.budget, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        HorizontalDivider()
        if (state.items.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                Text(
                    stringResource(R.string.calculator_empty),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(state.items, key = { it.id }) { item ->
                    SessionItemRow(
                        item = item,
                        onClick = if (state.isActive) ({ onEditItem(item.id) }) else null,
                        onRemove = if (state.isActive) ({ viewModel.onRemove(item) }) else null,
                    )
                }
            }
        }
        if (state.isActive) {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val entryText = if (state.entry.isEmpty()) "0,00 €" else "${state.entry} €"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            entryText,
                            style = MaterialTheme.typography.displaySmall,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = entryText },
                        )
                    }
                    NumericKeypad(onKey = viewModel::onKey)
                    Button(
                        onClick = viewModel::onAdd,
                        enabled = state.entryCents != null,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(stringResource(R.string.action_add_price), Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

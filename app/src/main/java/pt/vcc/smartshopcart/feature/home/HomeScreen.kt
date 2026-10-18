package pt.vcc.smartshopcart.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.smartshopcart.R
import pt.vcc.smartshopcart.core.money.MoneyFormatter
import pt.vcc.smartshopcart.core.ui.components.StartSessionDialog
import pt.vcc.smartshopcart.domain.model.SessionMode

@Composable
fun HomeScreen(
    onOpenSession: (sessionId: String, mode: SessionMode) -> Unit,
    onOpenLists: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val active by viewModel.activeSession.collectAsStateWithLifecycle()
    val stores by viewModel.stores.collectAsStateWithLifecycle()
    var showStartDialog by rememberSaveable { mutableStateOf(false) }
    var showActiveExists by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.OpenSession -> onOpenSession(event.sessionId, event.mode)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        active?.let { session ->
            ActiveSessionCard(session, onResume = { onOpenSession(session.sessionId, session.mode) })
        }
        HomeEntry(stringResource(R.string.home_calculator), stringResource(R.string.home_calculator_hint)) {
            if (active != null) showActiveExists = true else showStartDialog = true
        }
        HomeEntry(stringResource(R.string.home_lists), stringResource(R.string.home_lists_hint), onOpenLists)
    }

    if (showStartDialog) {
        StartSessionDialog(
            title = stringResource(R.string.home_calculator),
            stores = stores,
            initialBudgetCents = null,
            onDismiss = { showStartDialog = false },
            onConfirm = { store, budget ->
                showStartDialog = false
                viewModel.startCalculator(store, budget)
            },
        )
    }

    val current = active
    if (showActiveExists && current != null) {
        AlertDialog(
            onDismissRequest = { showActiveExists = false },
            title = { Text(stringResource(R.string.session_active_exists_title)) },
            text = { Text(stringResource(R.string.session_active_exists_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showActiveExists = false
                    onOpenSession(current.sessionId, current.mode)
                }) { Text(stringResource(R.string.action_resume)) }
            },
            dismissButton = {
                TextButton(onClick = { showActiveExists = false }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
}

@Composable
private fun ActiveSessionCard(session: ActiveSessionUi, onResume: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.session_in_progress), style = MaterialTheme.typography.titleLarge)
            val where = session.listName ?: session.storeName
            if (where != null) Text(where, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.session_in_progress_summary, session.itemCount, MoneyFormatter.format(session.totalCents)),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onResume, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.action_resume))
            }
        }
    }
}

@Composable
internal fun HomeEntry(title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

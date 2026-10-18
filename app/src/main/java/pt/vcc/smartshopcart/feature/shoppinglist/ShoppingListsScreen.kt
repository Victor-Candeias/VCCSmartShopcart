package pt.vcc.smartshopcart.feature.shoppinglist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.smartshopcart.R
import pt.vcc.smartshopcart.core.money.MoneyFormatter
import pt.vcc.smartshopcart.core.ui.components.ListDialog
import pt.vcc.smartshopcart.domain.model.ShoppingListSummary

@Composable
fun ShoppingListsScreen(
    onOpenList: (listId: String) -> Unit,
    viewModel: ShoppingListsViewModel = hiltViewModel(),
) {
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val showArchived by viewModel.showArchived.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) { viewModel.openList.collect(onOpenList) }

    Scaffold(
        floatingActionButton = {
            if (!showArchived) {
                ExtendedFloatingActionButton(
                    onClick = { showCreate = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.list_new)) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                stringResource(R.string.nav_lists),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showArchived,
                    onClick = { viewModel.setShowArchived(false) },
                    label = { Text(stringResource(R.string.lists_active)) },
                )
                FilterChip(
                    selected = showArchived,
                    onClick = { viewModel.setShowArchived(true) },
                    label = { Text(stringResource(R.string.lists_archived)) },
                )
            }
            val current = lists
            when {
                current == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                current.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text(
                        stringResource(if (showArchived) R.string.lists_archived_empty else R.string.lists_empty),
                        textAlign = TextAlign.Center,
                    )
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(current, key = { it.list.id }) { summary -> ListCard(summary) { onOpenList(summary.list.id) } }
                }
            }
        }
    }

    if (showCreate) {
        ListDialog(
            title = stringResource(R.string.list_new),
            initialName = "",
            initialBudgetCents = null,
            onDismiss = { showCreate = false },
            onConfirm = { name, budget ->
                showCreate = false
                viewModel.create(name, budget)
            },
        )
    }
}

@Composable
private fun ListCard(summary: ShoppingListSummary, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(summary.list.name, style = MaterialTheme.typography.titleMedium)
            val count = pluralStringResource(R.plurals.items_count, summary.itemCount, summary.itemCount)
            val budget = summary.list.budgetCents?.let { stringResource(R.string.list_budget_value, MoneyFormatter.format(it)) }
            Text(listOfNotNull(count, budget).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

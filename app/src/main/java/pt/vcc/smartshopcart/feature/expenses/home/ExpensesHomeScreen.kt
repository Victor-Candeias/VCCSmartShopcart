package pt.vcc.smartshopcart.feature.expenses.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.smartshopcart.R
import pt.vcc.smartshopcart.core.money.MoneyFormatter
import pt.vcc.smartshopcart.domain.model.expense.ExpenseEditorDraft
import pt.vcc.smartshopcart.feature.expenses.components.label

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpensesHomeScreen(
    onBack: () -> Unit,
    onNewExpense: (categoryId: String?, productId: String?) -> Unit,
    onResumeDraft: () -> Unit,
    onOpenHistory: (() -> Unit)?,
    onOpenCatalog: () -> Unit,
    viewModel: ExpensesHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_expenses)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val current = state
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            current.draft?.let { DraftCard(it, onResumeDraft) }
            Button(onClick = { onNewExpense(null, null) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.expense_new), Modifier.padding(start = 8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onOpenHistory != null) {
                    OutlinedButton(onClick = onOpenHistory, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.expense_history_title))
                    }
                }
                OutlinedButton(onClick = onOpenCatalog, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.expense_catalog_title))
                }
            }
            if (current.favorites.isNotEmpty()) {
                SectionTitle(stringResource(R.string.expense_favorites))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    current.favorites.forEach { product ->
                        SuggestionChip(
                            onClick = { onNewExpense(product.categoryId, product.id) },
                            label = { Text(listOfNotNull(product.icon, product.name).joinToString(" ")) },
                        )
                    }
                }
            }
            SectionTitle(stringResource(R.string.expense_categories))
            current.categories.forEach { category ->
                Card(onClick = { onNewExpense(category.id, null) }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        category.label(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun DraftCard(draft: ExpenseEditorDraft, onResume: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.expense_draft_title), style = MaterialTheme.typography.titleLarge)
            draft.establishmentName.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            val count = draft.lines.sumOf { it.quantity }
            Text(
                pluralStringResource(R.plurals.expense_draft_summary, count, count, MoneyFormatter.format(draft.totalCents)),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onResume, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.action_resume))
            }
        }
    }
}

package pt.vcc.smartshopcart.feature.history

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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.smartshopcart.R
import pt.vcc.smartshopcart.core.common.DateTimeText
import pt.vcc.smartshopcart.core.money.MoneyFormatter
import pt.vcc.smartshopcart.domain.repository.SessionSummary

@Composable
fun HistoryScreen(
    onOpenPurchase: (sessionId: String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val purchases by viewModel.purchases.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.nav_history),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp),
        )
        val current = purchases
        when {
            current == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            current.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                Text(stringResource(R.string.history_empty), textAlign = TextAlign.Center)
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(current, key = { it.session.id }) { summary ->
                    PurchaseCard(summary) { onOpenPurchase(summary.session.id) }
                }
            }
        }
    }
}

@Composable
private fun PurchaseCard(summary: SessionSummary, onClick: () -> Unit) {
    val session = summary.session
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    summary.storeName ?: stringResource(R.string.history_no_store),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    DateTimeText.dateTime(session.completedAt ?: session.startedAt),
                    style = MaterialTheme.typography.bodyMedium,
                )
                val count = pluralStringResource(R.plurals.items_count, summary.itemCount, summary.itemCount)
                val list = summary.listName?.let { stringResource(R.string.history_list_name, it) }
                Text(listOfNotNull(count, list).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                MoneyFormatter.format(session.finalTotalCents ?: 0),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

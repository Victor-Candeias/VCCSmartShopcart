package pt.vcc.smartshopcart.feature.sharedshopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Componente reutilizavel para a Calculadora Rapida.
 * Recebe o estado da sessao real e comunica alteracoes por callbacks.
 */
@Composable
fun SharedPurchasePanel(
    participants: List<PurchaseParticipant>,
    lines: List<PurchaseLine>,
    onAddParticipant: (String) -> Unit,
    onAssignLine: (lineId: String, participantId: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = remember(participants, lines) {
        SharedPurchaseCalculator.calculate(participants, lines)
    }
    var name by remember { mutableStateOf("") }

    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Compra partilhada", style = MaterialTheme.typography.titleLarge)
        Text("Total: ${summary.grandTotalCents.asEuro()}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome do cliente") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Button(
                enabled = name.isNotBlank(),
                onClick = { onAddParticipant(name.trim()); name = "" },
            ) { Text("Adicionar") }
        }
        participants.forEach { person ->
            Text("${person.name}: ${summary.totalsByParticipant.getValue(person.id).asEuro()}")
        }
        if (summary.unassignedCents > 0) {
            Text("Por atribuir: ${summary.unassignedCents.asEuro()}")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(lines, key = { it.id }) { line ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${line.name} · ${line.totalCents.asEuro()}")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            participants.forEach { person ->
                                Button(
                                    enabled = line.participantId != person.id,
                                    onClick = { onAssignLine(line.id, person.id) },
                                ) { Text(person.name) }
                            }
                        }
                    }
                }
            }
        }
        Text(if (summary.canFinish) "Todos os produtos atribuidos" else "Existem produtos por atribuir")
    }
}

private fun Long.asEuro(): String =
    java.text.NumberFormat.getCurrencyInstance(java.util.Locale.forLanguageTag("pt-PT"))
        .format(this / 100.0)

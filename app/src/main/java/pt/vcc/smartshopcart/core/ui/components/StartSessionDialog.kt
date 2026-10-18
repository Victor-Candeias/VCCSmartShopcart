package pt.vcc.smartshopcart.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pt.vcc.smartshopcart.R
import pt.vcc.smartshopcart.core.money.MoneyFormatter
import pt.vcc.smartshopcart.core.money.MoneyParser
import pt.vcc.smartshopcart.domain.model.Store

/** Escolha opcional de supermercado (D18) e orçamento antes de iniciar uma sessão. */
@Composable
fun StartSessionDialog(
    title: String,
    stores: List<Store>,
    initialBudgetCents: Long?,
    onDismiss: () -> Unit,
    onConfirm: (storeName: String?, budgetCents: Long?) -> Unit,
) {
    var storeName by rememberSaveable { mutableStateOf("") }
    var budget by rememberSaveable { mutableStateOf(initialBudgetCents?.let(MoneyFormatter::formatPlain) ?: "") }
    val budgetCents = MoneyParser.parseToCents(budget)
    val budgetInvalid = budget.isNotBlank() && budgetCents == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    label = { Text(stringResource(R.string.session_store_optional)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                    ),
                )
                if (stores.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(stores, key = { it.id }) { store ->
                            FilterChip(
                                selected = storeName.trim().equals(store.name, ignoreCase = true),
                                onClick = { storeName = store.name },
                                label = { Text(store.name) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = budget,
                    onValueChange = { budget = it },
                    label = { Text(stringResource(R.string.session_budget_optional)) },
                    suffix = { Text("€") },
                    isError = budgetInvalid,
                    supportingText = if (budgetInvalid) {
                        { Text(stringResource(R.string.error_invalid_amount)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !budgetInvalid,
                onClick = { onConfirm(storeName.trim().ifEmpty { null }, budgetCents?.takeIf { it > 0 }) },
            ) { Text(stringResource(R.string.action_start)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

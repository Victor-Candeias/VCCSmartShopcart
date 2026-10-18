package pt.vcc.smartshopcart.feature.expenses.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import pt.vcc.smartshopcart.R
import pt.vcc.smartshopcart.core.money.MoneyFormatter
import pt.vcc.smartshopcart.core.money.MoneyParser
import pt.vcc.smartshopcart.core.ui.components.AmountField
import pt.vcc.smartshopcart.core.ui.components.NameField
import pt.vcc.smartshopcart.domain.model.expense.ExpenseCategory

/** Criação/edição de um produto do catálogo de despesas (categoria, nome, ícone, preço habitual). */
@Composable
fun ExpenseProductDialog(
    title: String,
    categories: List<ExpenseCategory>,
    initialCategoryId: String?,
    initialName: String,
    initialIcon: String?,
    initialDefaultPriceCents: Long?,
    onDismiss: () -> Unit,
    onConfirm: (categoryId: String, name: String, icon: String?, defaultPriceCents: Long?) -> Unit,
) {
    var categoryId by rememberSaveable { mutableStateOf(initialCategoryId ?: categories.firstOrNull()?.id) }
    var name by rememberSaveable { mutableStateOf(initialName) }
    var icon by rememberSaveable { mutableStateOf(initialIcon.orEmpty()) }
    var price by rememberSaveable { mutableStateOf(initialDefaultPriceCents?.let(MoneyFormatter::formatPlain).orEmpty()) }
    val priceCents = MoneyParser.parseToCents(price)
    val priceInvalid = price.isNotBlank() && (priceCents == null || priceCents < 0)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.expense_product_category), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = category.id == categoryId,
                            onClick = { categoryId = category.id },
                            label = { Text(category.label()) },
                        )
                    }
                }
                NameField(name, { name = it }, stringResource(R.string.expense_product_name), Modifier.focusRequester(focus))
                NameField(icon, { icon = it.take(MAX_ICON_LENGTH) }, stringResource(R.string.expense_product_icon))
                AmountField(
                    price,
                    { price = it },
                    stringResource(R.string.expense_product_default_price),
                    priceInvalid,
                    imeAction = ImeAction.Done,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && categoryId != null && !priceInvalid,
                onClick = { onConfirm(categoryId!!, name.trim(), icon.trim().ifEmpty { null }, priceCents) },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val MAX_ICON_LENGTH = 8

/** Nome da categoria precedido do ícone, quando existe. */
fun ExpenseCategory.label(): String = listOfNotNull(icon, name).joinToString(" ")

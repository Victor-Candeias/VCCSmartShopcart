package pt.vcc.smartshopcart.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pt.vcc.smartshopcart.R
import pt.vcc.smartshopcart.core.money.LineTotalCalculator
import pt.vcc.smartshopcart.core.money.MoneyFormatter
import pt.vcc.smartshopcart.core.money.MoneyParser
import pt.vcc.smartshopcart.core.money.QuantityParser
import pt.vcc.smartshopcart.domain.model.MeasureUnit
import java.math.BigDecimal

@Composable
internal fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    invalid: Boolean,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        suffix = { Text("€") },
        isError = invalid,
        supportingText = if (invalid) ({ Text(stringResource(R.string.error_invalid_amount)) }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
internal fun QuantityField(value: String, onValueChange: (String) -> Unit, unit: MeasureUnit, invalid: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.item_quantity)) },
        suffix = { Text(unit.symbol) },
        isError = invalid,
        supportingText = if (invalid) ({ Text(stringResource(R.string.error_invalid_quantity)) }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun NameField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Nome e orçamento opcional de uma lista. */
@Composable
fun ListDialog(
    title: String,
    initialName: String,
    initialBudgetCents: Long?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, budgetCents: Long?) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var budget by rememberSaveable { mutableStateOf(initialBudgetCents?.let(MoneyFormatter::formatPlain).orEmpty()) }
    val budgetCents = MoneyParser.parseToCents(budget)
    val budgetInvalid = budget.isNotBlank() && budgetCents == null
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NameField(name, { name = it }, stringResource(R.string.list_name), Modifier.focusRequester(focus))
                AmountField(budget, { budget = it }, stringResource(R.string.session_budget_optional), budgetInvalid, imeAction = ImeAction.Done)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !budgetInvalid,
                onClick = { onConfirm(name.trim(), budgetCents?.takeIf { it > 0 }) },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Item planeado: nome, quantidade, unidade e preço estimado opcional. */
@Composable
fun ListItemDialog(
    title: String,
    initialName: String,
    initialQuantity: BigDecimal,
    initialUnit: MeasureUnit,
    initialEstimatedCents: Long?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, quantity: BigDecimal, unit: MeasureUnit, estimatedCents: Long?) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var quantity by rememberSaveable { mutableStateOf(QuantityParser.format(initialQuantity)) }
    var unit by rememberSaveable { mutableStateOf(initialUnit) }
    var estimate by rememberSaveable { mutableStateOf(initialEstimatedCents?.let(MoneyFormatter::formatPlain).orEmpty()) }
    val quantityValue = QuantityParser.parse(quantity)
    val estimateCents = MoneyParser.parseToCents(estimate)
    val estimateInvalid = estimate.isNotBlank() && estimateCents == null
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NameField(name, { name = it }, stringResource(R.string.list_item_name), Modifier.focusRequester(focus))
                UnitSelector(unit, { unit = it })
                QuantityField(quantity, { quantity = it }, unit, quantityValue == null)
                AmountField(
                    estimate,
                    { estimate = it },
                    if (unit.priceUnit == MeasureUnit.UN) {
                        stringResource(R.string.list_item_estimate)
                    } else {
                        stringResource(R.string.list_item_estimate_per, unit.priceUnit.symbol)
                    },
                    estimateInvalid,
                    imeAction = ImeAction.Done,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && quantityValue != null && !estimateInvalid,
                onClick = { onConfirm(name.trim(), quantityValue!!, unit, estimateCents) },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Registo rápido de preço numa sessão planeada (item planeado ou extra). */
@Composable
fun AddPriceDialog(
    title: String,
    askName: Boolean,
    initialQuantity: BigDecimal,
    unit: MeasureUnit,
    initialPriceCents: Long?,
    onDismiss: () -> Unit,
    onConfirm: (name: String?, priceCents: Long, quantity: BigDecimal) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf(initialPriceCents?.let(MoneyFormatter::formatPlain).orEmpty()) }
    var quantity by rememberSaveable { mutableStateOf(QuantityParser.format(initialQuantity)) }
    val priceCents = MoneyParser.parseToCents(price)?.takeIf { it > 0 }
    val quantityValue = QuantityParser.parse(quantity)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (askName) NameField(name, { name = it }, stringResource(R.string.item_name))
                AmountField(
                    price,
                    { price = it },
                    if (unit.priceUnit == MeasureUnit.UN) {
                        stringResource(R.string.item_price)
                    } else {
                        stringResource(R.string.item_price_per, unit.priceUnit.symbol)
                    },
                    price.isNotBlank() && priceCents == null,
                    Modifier.focusRequester(focus),
                )
                QuantityField(quantity, { quantity = it }, unit, quantityValue == null)
                if (priceCents != null && quantityValue != null) {
                    Text(
                        stringResource(
                            R.string.item_line_total,
                            MoneyFormatter.format(LineTotalCalculator.lineTotalCents(priceCents, quantityValue, unit)),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = priceCents != null && quantityValue != null,
                onClick = { onConfirm(name.trim().ifEmpty { null }, priceCents!!, quantityValue!!) },
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

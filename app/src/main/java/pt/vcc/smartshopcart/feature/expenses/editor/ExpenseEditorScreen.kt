package pt.vcc.smartshopcart.feature.expenses.editor

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.smartshopcart.R
import pt.vcc.smartshopcart.core.common.DateTimeText
import pt.vcc.smartshopcart.core.money.MoneyFormatter
import pt.vcc.smartshopcart.core.money.MoneyParser
import pt.vcc.smartshopcart.core.ui.components.KeypadInput
import pt.vcc.smartshopcart.core.ui.components.NumericKeypad
import pt.vcc.smartshopcart.domain.model.expense.ExpenseEditorLine
import pt.vcc.smartshopcart.domain.model.expense.PriceSuggestionSource
import pt.vcc.smartshopcart.domain.usecase.expense.ExpenseDateTime
import pt.vcc.smartshopcart.domain.usecase.expense.ExpenseValidation
import pt.vcc.smartshopcart.feature.expenses.components.ExpenseProductDialog
import pt.vcc.smartshopcart.feature.expenses.components.label
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditorScreen(
    onBack: () -> Unit,
    onFinished: () -> Unit,
    viewModel: ExpenseEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pending by viewModel.pendingDraft.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val storageError = stringResource(R.string.error_storage)
    var priceLineKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showDate by rememberSaveable { mutableStateOf(false) }
    var showTime by rememberSaveable { mutableStateOf(false) }
    var showNewProduct by rememberSaveable { mutableStateOf(false) }
    var newProductToCatalog by rememberSaveable { mutableStateOf(false) }
    var confirmCancel by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ExpenseEditorEvent.Saved -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.expense_saved, MoneyFormatter.format(event.totalCents)),
                        Toast.LENGTH_SHORT,
                    ).show()
                    onFinished()
                }
                ExpenseEditorEvent.Closed -> onFinished()
                ExpenseEditorEvent.StorageError -> snackbar.showSnackbar(storageError)
            }
        }
    }

    // Sair sem cancelar mantém o rascunho para retomar mais tarde (§2.4).
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if ((state?.draft?.editingExpenseId != null)) R.string.expense_edit else R.string.expense_new)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val current = state
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                if (pending == null) CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            EditorContent(
                state = current,
                viewModel = viewModel,
                onEditPrice = { priceLineKey = it },
                onPickDate = { showDate = true },
                onPickTime = { showTime = true },
                onNewProduct = {
                    newProductToCatalog = false
                    showNewProduct = true
                },
                onUseQueryOnce = { viewModel.addQueryAsTemporary()?.let { priceLineKey = it } },
                onSaveQueryToCatalog = {
                    newProductToCatalog = true
                    showNewProduct = true
                },
                modifier = Modifier.weight(1f),
            )
            TotalBar(
                totalCents = current.draft.totalCents,
                missingPrices = current.draft.missingPriceCount,
                error = current.error,
                onCancel = { if (current.draft.isBlank) viewModel.cancel() else confirmCancel = true },
                onSave = viewModel::save,
            )
        }
    }

    val current = state
    val priceLine = priceLineKey?.let { key -> current?.draft?.lines?.firstOrNull { it.key == key } }
    if (priceLine != null) {
        PriceDialog(
            line = priceLine,
            onDismiss = { priceLineKey = null },
            onConfirm = { cents ->
                priceLineKey = null
                viewModel.setPrice(priceLine.key, cents)
            },
        )
    }
    if (showDate && current != null) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = ExpenseDateTime.utcDateMillis(current.draft.occurredAt))
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    showDate = false
                    pickerState.selectedDateMillis?.let(viewModel::setDate)
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(pickerState) }
    }
    if (showTime && current != null) {
        val time = ExpenseDateTime.localTime(current.draft.occurredAt)
        val pickerState = rememberTimePickerState(time.hour, time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text(stringResource(R.string.expense_time)) },
            text = { TimePicker(pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    showTime = false
                    viewModel.setTime(pickerState.hour, pickerState.minute)
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (showNewProduct && current != null) {
        ExpenseProductDialog(
            title = stringResource(R.string.expense_product_new),
            categories = current.categories,
            initialCategoryId = current.draft.selectedCategoryId,
            initialName = current.query,
            initialIcon = null,
            initialDefaultPriceCents = null,
            onDismiss = { showNewProduct = false },
            onConfirm = { categoryId, name, icon, price ->
                showNewProduct = false
                viewModel.createProduct(categoryId, name, icon, price)
            },
            allowTemporary = !newProductToCatalog,
            onConfirmTemporary = { name, price ->
                showNewProduct = false
                viewModel.addTemporaryProduct(name, price)
            },
        )
    }
    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(stringResource(R.string.expense_cancel_title)) },
            text = {
                Text(stringResource(if ((state?.draft?.editingExpenseId != null)) R.string.expense_cancel_edit_text else R.string.expense_cancel_text))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    viewModel.cancel()
                }) { Text(stringResource(R.string.action_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text(stringResource(R.string.action_keep_editing)) }
            },
        )
    }
    pending?.let { stored ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.expense_draft_conflict_title)) },
            text = {
                val count = stored.lines.sumOf { it.quantity }
                Text(
                    stringResource(R.string.expense_draft_conflict_text) + "\n\n" +
                        pluralStringResource(R.plurals.expense_draft_summary, count, count, MoneyFormatter.format(stored.totalCents)),
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::continuePendingDraft) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::discardPendingDraft) { Text(stringResource(R.string.action_discard)) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorContent(
    state: ExpenseEditorUiState,
    viewModel: ExpenseEditorViewModel,
    onEditPrice: (String) -> Unit,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    onNewProduct: () -> Unit,
    onUseQueryOnce: () -> Unit,
    onSaveQueryToCatalog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.draft
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        item {
            OutlinedTextField(
                value = draft.establishmentName,
                onValueChange = viewModel::setEstablishment,
                label = { Text(stringResource(R.string.expense_establishment)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.establishmentSuggestions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.establishmentSuggestions.forEach { name ->
                        SuggestionChip(onClick = { viewModel.setEstablishment(name) }, label = { Text(name) })
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val dateText = DateTimeText.date(draft.occurredAt)
                val timeText = TIME_FORMAT.format(ExpenseDateTime.localTime(draft.occurredAt))
                val dateDescription = stringResource(R.string.expense_date_value, dateText)
                val timeDescription = stringResource(R.string.expense_time_value, timeText)
                AssistChip(
                    onClick = onPickDate,
                    label = { Text(dateText) },
                    leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                    modifier = Modifier.semantics { contentDescription = dateDescription },
                )
                AssistChip(
                    onClick = onPickTime,
                    label = { Text(timeText) },
                    modifier = Modifier.semantics { contentDescription = timeDescription },
                )
            }
        }
        item {
            Text(
                stringResource(R.string.expense_selected),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp).semantics { heading() },
            )
            if (draft.lines.isEmpty()) {
                Text(
                    stringResource(R.string.expense_no_items),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
        items(draft.lines, key = { it.key }) { line ->
            ExpenseLineRow(
                line = line,
                onDecrease = { viewModel.changeQuantity(line.key, -1) },
                onIncrease = { viewModel.changeQuantity(line.key, +1) },
                onEditPrice = { onEditPrice(line.key) },
                onRemove = { viewModel.removeLine(line.key) },
            )
            HorizontalDivider()
        }
        item {
            Text(
                stringResource(R.string.expense_add_product),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp).semantics { heading() },
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text(stringResource(R.string.expense_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (state.query.isNotEmpty()) {
                    {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.expense_search_clear))
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = draft.selectedCategoryId == null,
                    onClick = { viewModel.selectCategory(null) },
                    label = { Text(stringResource(R.string.expense_category_all)) },
                )
                state.categories.forEach { category ->
                    FilterChip(
                        selected = draft.selectedCategoryId == category.id,
                        onClick = { viewModel.selectCategory(category.id) },
                        label = { Text(category.label()) },
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.newProductName?.let { name ->
                    AssistChip(
                        onClick = onUseQueryOnce,
                        label = { Text(stringResource(R.string.expense_query_use_once, name)) },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    )
                    AssistChip(
                        onClick = onSaveQueryToCatalog,
                        label = { Text(stringResource(R.string.expense_query_save_catalog, name)) },
                    )
                }
                state.panelProducts.forEach { product ->
                    val name = listOfNotNull(product.icon, product.name).joinToString(" ")
                    val description = stringResource(R.string.expense_add_named, product.name)
                    SuggestionChip(
                        onClick = { viewModel.addProduct(product) },
                        label = { Text(name) },
                        modifier = Modifier.semantics { contentDescription = description },
                    )
                }
                if (state.newProductName == null) {
                    AssistChip(
                        onClick = onNewProduct,
                        label = { Text(stringResource(R.string.expense_product_new)) },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    )
                }
            }
            OutlinedTextField(
                value = draft.notes,
                onValueChange = viewModel::setNotes,
                label = { Text(stringResource(R.string.expense_notes)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun ExpenseLineRow(
    line: ExpenseEditorLine,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onEditPrice: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(line.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.expense_remove_line, line.name))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val decreaseDescription = stringResource(R.string.expense_quantity_decrease, line.name)
            FilledTonalIconButton(
                onClick = onDecrease,
                enabled = line.quantity > 1,
                modifier = Modifier.semantics { contentDescription = decreaseDescription },
            ) {
                Text("−", style = MaterialTheme.typography.titleLarge)
            }
            val quantityDescription = stringResource(R.string.expense_quantity_value, line.quantity)
            Text(
                line.quantity.toString(),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 32.dp).semantics {
                    contentDescription = quantityDescription
                    liveRegion = LiveRegionMode.Polite
                },
            )
            FilledTonalIconButton(onClick = onIncrease) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.expense_quantity_increase, line.name))
            }
            val price = line.unitPriceCents
            val priceText = if (price == null) stringResource(R.string.expense_set_price) else "× ${MoneyFormatter.format(price)}"
            val priceDescription = stringResource(R.string.expense_price_for, line.name) + ", " +
                (price?.let(MoneyFormatter::format) ?: stringResource(R.string.expense_set_price))
            TextButton(
                onClick = onEditPrice,
                modifier = Modifier.weight(1f).semantics { contentDescription = priceDescription },
            ) {
                Text(
                    priceText,
                    color = if (price == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                line.subtotalCents?.let(MoneyFormatter::format) ?: "—",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        line.priceSource?.let { source ->
            Text(
                stringResource(R.string.expense_price_suggested, stringResource(source.labelRes())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun PriceSuggestionSource.labelRes(): Int = when (this) {
    PriceSuggestionSource.SAME_ESTABLISHMENT -> R.string.expense_price_source_same
    PriceSuggestionSource.LAST_PAID -> R.string.expense_price_source_last
    PriceSuggestionSource.CATALOG -> R.string.expense_price_source_catalog
}

@Composable
private fun TotalBar(
    totalCents: Long,
    missingPrices: Int,
    error: ExpenseValidation?,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val totalText = MoneyFormatter.format(totalCents)
            val totalDescription = stringResource(R.string.expense_total_value, totalText)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.expense_total), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(
                    totalText,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics {
                        contentDescription = totalDescription
                        liveRegion = LiveRegionMode.Polite
                    },
                )
            }
            val message = when (error) {
                ExpenseValidation.NoItems -> stringResource(R.string.expense_error_no_items)
                ExpenseValidation.MissingPrice -> stringResource(R.string.expense_error_missing_price)
                ExpenseValidation.InvalidValues -> stringResource(R.string.expense_error_invalid)
                else -> if (missingPrices > 0) pluralStringResource(R.plurals.expense_missing_prices, missingPrices, missingPrices) else null
            }
            if (message != null) {
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(onClick = onSave, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

/** Preço unitário com o teclado numérico da calculadora; aceita 0 (oferta) mas não valores negativos. */
@Composable
private fun PriceDialog(line: ExpenseEditorLine, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var entry by rememberSaveable(line.key) { mutableStateOf(line.unitPriceCents?.let(MoneyFormatter::formatPlain).orEmpty()) }
    val cents = MoneyParser.parseToCents(entry)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.expense_price_for, line.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val shown = if (entry.isEmpty()) "0,00 €" else "$entry €"
                Text(
                    shown,
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = shown
                        liveRegion = LiveRegionMode.Polite
                    },
                )
                NumericKeypad(onKey = { entry = KeypadInput.apply(entry, it) })
            }
        },
        confirmButton = {
            TextButton(enabled = cents != null, onClick = { onConfirm(cents!!) }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

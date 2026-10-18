package pt.vcc.smartshopcart.feature.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.smartshopcart.R
import pt.vcc.smartshopcart.core.money.MoneyFormatter
import pt.vcc.smartshopcart.domain.model.MeasureUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditorScreen(
    onBack: () -> Unit,
    viewModel: ItemEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ItemEditorEvent.Done -> onBack()
                ItemEditorEvent.SessionNotActive -> snackbar.showSnackbar(context.getString(R.string.session_not_active))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.item_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (!state.missing && !state.loading) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_remove_item))
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
                Text(stringResource(R.string.item_missing))
            }
            else -> ItemFormContent(state, viewModel, Modifier.padding(padding))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.item_delete_title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.onDelete()
                }) { Text(stringResource(R.string.action_remove_item)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemFormContent(state: ItemEditorUiState, viewModel: ItemEditorViewModel, modifier: Modifier = Modifier) {
    val form = state.form
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.barcode?.let {
            Text(stringResource(R.string.item_barcode, it), style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedTextField(
            value = form.name,
            onValueChange = { v -> viewModel.onFormChange { it.copy(name = v) } },
            label = { Text(stringResource(R.string.item_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.item_unit), style = MaterialTheme.typography.labelLarge)
        val units = MeasureUnit.entries
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            units.forEachIndexed { index, unit ->
                SegmentedButton(
                    selected = form.unit == unit,
                    onClick = { viewModel.onFormChange { it.copy(unit = unit) } },
                    shape = SegmentedButtonDefaults.itemShape(index, units.size),
                ) { Text(unit.symbol) }
            }
        }
        OutlinedTextField(
            value = form.price,
            onValueChange = { v -> viewModel.onFormChange { it.copy(price = v) } },
            label = {
                Text(
                    if (form.unit.priceUnit == MeasureUnit.UN) {
                        stringResource(R.string.item_price)
                    } else {
                        stringResource(R.string.item_price_per, form.unit.priceUnit.symbol)
                    },
                )
            },
            suffix = { Text("€") },
            isError = form.priceError,
            supportingText = if (form.priceError) ({ Text(stringResource(R.string.error_invalid_amount)) }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.quantity,
            onValueChange = { v -> viewModel.onFormChange { it.copy(quantity = v) } },
            label = { Text(stringResource(R.string.item_quantity)) },
            suffix = { Text(form.unit.symbol) },
            isError = form.quantityError,
            supportingText = if (form.quantityError) ({ Text(stringResource(R.string.error_invalid_quantity)) }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.discount,
            onValueChange = { v -> viewModel.onFormChange { it.copy(discount = v) } },
            label = { Text(stringResource(R.string.item_discount)) },
            suffix = { Text("€") },
            isError = form.discountError,
            supportingText = if (form.discountError) ({ Text(stringResource(R.string.error_invalid_amount)) }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.item_line_total, form.totalCents?.let(MoneyFormatter::format) ?: "—"),
            style = MaterialTheme.typography.titleLarge,
        )
        Button(
            onClick = viewModel::onSave,
            enabled = form.totalCents != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.action_save)) }
    }
}

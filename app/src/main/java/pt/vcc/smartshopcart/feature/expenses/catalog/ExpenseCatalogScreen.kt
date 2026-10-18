package pt.vcc.smartshopcart.feature.expenses.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.smartshopcart.R
import pt.vcc.smartshopcart.core.money.MoneyFormatter
import pt.vcc.smartshopcart.domain.model.expense.ExpenseProduct
import pt.vcc.smartshopcart.feature.expenses.components.ExpenseProductDialog
import pt.vcc.smartshopcart.feature.expenses.components.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseCatalogScreen(onBack: () -> Unit, viewModel: ExpenseCatalogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val errorText = stringResource(R.string.error_storage)

    LaunchedEffect(viewModel) { viewModel.errors.collect { snackbar.showSnackbar(errorText) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.expense_catalog_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            val label = stringResource(R.string.expense_product_new)
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(label) },
                modifier = Modifier.semantics { contentDescription = label },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val current = state
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = current.query,
                onValueChange = viewModel::setQuery,
                label = { Text(stringResource(R.string.expense_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (current.query.isNotEmpty()) {
                    {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.expense_search_clear))
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = current.selectedCategoryId == null,
                    onClick = { viewModel.selectCategory(null) },
                    label = { Text(stringResource(R.string.expense_category_all)) },
                )
                current.categories.forEach { category ->
                    FilterChip(
                        selected = current.selectedCategoryId == category.id,
                        onClick = { viewModel.selectCategory(category.id) },
                        label = { Text(category.label()) },
                    )
                }
                FilterChip(
                    selected = current.showHidden,
                    onClick = { viewModel.setShowHidden(!current.showHidden) },
                    label = { Text(stringResource(R.string.expense_catalog_show_hidden)) },
                )
            }
            if (current.rows.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text(stringResource(R.string.expense_catalog_empty), textAlign = TextAlign.Center)
                }
            } else {
                val categoryNames = current.categories.associate { it.id to it.name }
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(current.rows, key = { it.product.id }) { row ->
                        CatalogProductRow(
                            row = row,
                            categoryName = categoryNames[row.product.categoryId].takeIf { current.selectedCategoryId == null },
                            onEdit = { editingId = row.product.id },
                            onToggleFavorite = { viewModel.toggleFavorite(row.product) },
                            onToggleHidden = { viewModel.toggleHidden(row.product) },
                            onMove = { up -> viewModel.move(row.product, up) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    val categories = state?.categories.orEmpty()
    if (creating) {
        ExpenseProductDialog(
            title = stringResource(R.string.expense_product_new),
            categories = categories,
            initialCategoryId = state?.selectedCategoryId,
            initialName = "",
            initialIcon = null,
            initialDefaultPriceCents = null,
            onDismiss = { creating = false },
            onConfirm = { categoryId, name, icon, price ->
                creating = false
                viewModel.save(null, categoryId, name, icon, price)
            },
        )
    }
    val editing = editingId?.let { id -> state?.rows?.firstOrNull { it.product.id == id }?.product }
    if (editing != null) {
        ExpenseProductDialog(
            title = stringResource(R.string.expense_product_edit),
            categories = categories,
            initialCategoryId = editing.categoryId,
            initialName = editing.name,
            initialIcon = editing.icon,
            initialDefaultPriceCents = editing.defaultPriceCents,
            onDismiss = { editingId = null },
            onConfirm = { categoryId, name, icon, price ->
                editingId = null
                viewModel.save(editing.id, categoryId, name, icon, price)
            },
        )
    }
}

@Composable
private fun CatalogProductRow(
    row: CatalogRow,
    categoryName: String?,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleHidden: () -> Unit,
    onMove: (up: Boolean) -> Unit,
) {
    val product = row.product
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(listOfNotNull(product.icon, product.name).joinToString(" ")) },
        supportingContent = { Text(productDetails(product, categoryName)) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (product.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = stringResource(
                            if (product.isFavorite) R.string.expense_favorite_remove else R.string.expense_favorite_add,
                            product.name,
                        ),
                        tint = if (product.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onMove(true) }, enabled = row.canMoveUp) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.expense_move_up, product.name))
                }
                IconButton(onClick = { onMove(false) }, enabled = row.canMoveDown) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.expense_move_down, product.name))
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.expense_product_edit)) },
                            onClick = {
                                menuOpen = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(if (product.isActive) R.string.expense_product_hide else R.string.expense_product_show))
                            },
                            onClick = {
                                menuOpen = false
                                onToggleHidden()
                            },
                        )
                    }
                }
            }
        },
        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.expense_product_edit), onClick = onEdit),
    )
}

@Composable
private fun productDetails(product: ExpenseProduct, categoryName: String?): String {
    val price = product.defaultPriceCents?.let { stringResource(R.string.expense_default_price_value, MoneyFormatter.format(it)) }
        ?: stringResource(R.string.expense_no_default_price)
    val hidden = if (!product.isActive) stringResource(R.string.expense_product_hidden) else null
    return listOfNotNull(categoryName, price, hidden).joinToString(" · ")
}

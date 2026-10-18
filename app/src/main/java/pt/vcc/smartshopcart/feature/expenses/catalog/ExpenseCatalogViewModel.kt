package pt.vcc.smartshopcart.feature.expenses.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.vcc.smartshopcart.data.seed.ExpenseCatalogSeeder
import pt.vcc.smartshopcart.domain.model.expense.ExpenseCategory
import pt.vcc.smartshopcart.domain.model.expense.ExpenseProduct
import pt.vcc.smartshopcart.domain.usecase.expense.ExpenseSearch
import pt.vcc.smartshopcart.domain.usecase.expense.GetExpenseCatalogUseCase
import pt.vcc.smartshopcart.domain.usecase.expense.MoveExpenseProductUseCase
import pt.vcc.smartshopcart.domain.usecase.expense.SaveExpenseProductUseCase
import pt.vcc.smartshopcart.domain.usecase.expense.SetExpenseProductActiveUseCase
import pt.vcc.smartshopcart.domain.usecase.expense.SetExpenseProductFavoriteUseCase
import javax.inject.Inject

data class CatalogRow(val product: ExpenseProduct, val canMoveUp: Boolean, val canMoveDown: Boolean)

data class ExpenseCatalogUiState(
    val categories: List<ExpenseCategory>,
    val selectedCategoryId: String?,
    val query: String,
    val showHidden: Boolean,
    val rows: List<CatalogRow>,
)

@HiltViewModel
class ExpenseCatalogViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    getCatalog: GetExpenseCatalogUseCase,
    seeder: ExpenseCatalogSeeder,
    private val saveProduct: SaveExpenseProductUseCase,
    private val setFavorite: SetExpenseProductFavoriteUseCase,
    private val setActive: SetExpenseProductActiveUseCase,
    private val moveProduct: MoveExpenseProductUseCase,
) : ViewModel() {

    private val selectedCategoryId = savedStateHandle.getStateFlow<String?>(KEY_CATEGORY, null)
    private val query = savedStateHandle.getStateFlow(KEY_QUERY, "")
    private val showHidden = savedStateHandle.getStateFlow(KEY_HIDDEN, false)

    private val _errors = Channel<Unit>(Channel.BUFFERED)
    val errors: Flow<Unit> = _errors.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ExpenseCatalogUiState?> = showHidden
        .flatMapLatest { hidden -> getCatalog(includeHidden = hidden) }
        .let { catalog ->
            combine(catalog, selectedCategoryId, query, showHidden) { c, categoryId, q, hidden ->
                val visibleCategory = categoryId?.takeIf { id -> c.categories.any { it.id == id } }
                val byCategory = c.products.groupBy { it.categoryId }
                val rows = ExpenseSearch.filter(c.products, visibleCategory, q).map { product ->
                    val siblings = byCategory.getValue(product.categoryId)
                    CatalogRow(
                        product = product,
                        canMoveUp = siblings.first().id != product.id,
                        canMoveDown = siblings.last().id != product.id,
                    )
                }
                ExpenseCatalogUiState(c.categories, visibleCategory, q, hidden, rows)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { runCatching { seeder.seedIfNeeded() } }
    }

    fun selectCategory(categoryId: String?) {
        savedStateHandle[KEY_CATEGORY] = categoryId
    }

    fun setQuery(text: String) {
        savedStateHandle[KEY_QUERY] = text
    }

    fun setShowHidden(show: Boolean) {
        savedStateHandle[KEY_HIDDEN] = show
    }

    fun save(id: String?, categoryId: String, name: String, icon: String?, defaultPriceCents: Long?) = launchSafely {
        saveProduct(id, categoryId, name, icon, defaultPriceCents)
    }

    fun toggleFavorite(product: ExpenseProduct) = launchSafely { setFavorite(product.id, !product.isFavorite) }

    fun toggleHidden(product: ExpenseProduct) = launchSafely { setActive(product.id, !product.isActive) }

    fun move(product: ExpenseProduct, up: Boolean) = launchSafely {
        moveProduct(product.id, up, includeHidden = showHidden.value)
    }

    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() }.onFailure { _errors.send(Unit) } }
    }

    private companion object {
        const val KEY_CATEGORY = "category"
        const val KEY_QUERY = "query"
        const val KEY_HIDDEN = "hidden"
    }
}

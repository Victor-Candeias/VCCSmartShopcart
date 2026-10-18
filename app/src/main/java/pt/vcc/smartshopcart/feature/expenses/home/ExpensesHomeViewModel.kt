package pt.vcc.smartshopcart.feature.expenses.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.vcc.smartshopcart.data.seed.ExpenseCatalogSeeder
import pt.vcc.smartshopcart.domain.model.expense.ExpenseCategory
import pt.vcc.smartshopcart.domain.model.expense.ExpenseEditorDraft
import pt.vcc.smartshopcart.domain.model.expense.ExpenseProduct
import pt.vcc.smartshopcart.domain.repository.ExpenseDraftStore
import pt.vcc.smartshopcart.domain.usecase.expense.GetExpenseCatalogUseCase
import javax.inject.Inject

data class ExpensesHomeUiState(
    val categories: List<ExpenseCategory>,
    val favorites: List<ExpenseProduct>,
    /** Despesa interrompida que pode ser retomada. */
    val draft: ExpenseEditorDraft?,
)

@HiltViewModel
class ExpensesHomeViewModel @Inject constructor(
    getCatalog: GetExpenseCatalogUseCase,
    drafts: ExpenseDraftStore,
    seeder: ExpenseCatalogSeeder,
) : ViewModel() {

    val state: StateFlow<ExpensesHomeUiState?> =
        combine(getCatalog(), drafts.draft) { catalog, draft ->
            ExpensesHomeUiState(
                categories = catalog.categories,
                favorites = catalog.products.filter { it.isFavorite },
                draft = draft?.takeUnless { it.isBlank },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { runCatching { seeder.seedIfNeeded() } }
    }
}

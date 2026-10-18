package pt.vcc.smartshopcart.feature.expenses.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.vcc.smartshopcart.domain.model.expense.ExpenseCategory
import pt.vcc.smartshopcart.domain.model.expense.ExpenseSummary
import pt.vcc.smartshopcart.domain.repository.ExpenseRepository
import pt.vcc.smartshopcart.domain.usecase.expense.DeleteExpenseUseCase
import pt.vcc.smartshopcart.domain.usecase.expense.ExpenseHistoryFilter
import javax.inject.Inject

data class ExpenseHistoryUiState(
    val expenses: List<ExpenseSummary>,
    val categories: List<ExpenseCategory>,
    val establishments: List<String>,
    val categoryId: String?,
    val establishment: String?,
    /** Soma das despesas visíveis com os filtros atuais. */
    val totalCents: Long,
    val hasAnyExpense: Boolean,
)

@HiltViewModel
class ExpenseHistoryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    repository: ExpenseRepository,
    private val deleteExpense: DeleteExpenseUseCase,
) : ViewModel() {

    private val _deleteFailed = Channel<Unit>(Channel.BUFFERED)
    val deleteFailed: Flow<Unit> = _deleteFailed.receiveAsFlow()

    private val categoryId = savedStateHandle.getStateFlow<String?>(KEY_CATEGORY, null)
    private val establishment = savedStateHandle.getStateFlow<String?>(KEY_ESTABLISHMENT, null)

    val state: StateFlow<ExpenseHistoryUiState?> = combine(
        repository.observeExpenses(),
        repository.observeCategories(includeInactive = true),
        categoryId,
        establishment,
    ) { all, categories, category, place ->
        val used = all.flatMap { it.categoryIds }.toSet()
        val visible = ExpenseHistoryFilter.apply(all, category, place)
        ExpenseHistoryUiState(
            expenses = visible,
            categories = categories.filter { it.id in used },
            establishments = ExpenseHistoryFilter.establishments(all),
            categoryId = category,
            establishment = place,
            totalCents = visible.sumOf { it.expense.totalCents },
            hasAnyExpense = all.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectCategory(id: String?) {
        savedStateHandle[KEY_CATEGORY] = id
    }

    fun selectEstablishment(name: String?) {
        savedStateHandle[KEY_ESTABLISHMENT] = name
    }

    fun delete(expenseId: String) {
        viewModelScope.launch {
            runCatching { deleteExpense(expenseId) }.onFailure { _deleteFailed.send(Unit) }
        }
    }

    private companion object {
        const val KEY_CATEGORY = "category"
        const val KEY_ESTABLISHMENT = "establishment"
    }
}

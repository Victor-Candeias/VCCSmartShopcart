package pt.vcc.smartshopcart.feature.expenses.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.vcc.smartshopcart.core.navigation.ExpenseDetailRoute
import pt.vcc.smartshopcart.domain.model.expense.ExpenseCategory
import pt.vcc.smartshopcart.domain.model.expense.ExpenseDetails
import pt.vcc.smartshopcart.domain.repository.ExpenseRepository
import pt.vcc.smartshopcart.domain.usecase.expense.DeleteExpenseUseCase
import javax.inject.Inject

data class ExpenseDetailUiState(
    val loading: Boolean,
    val details: ExpenseDetails?,
    val categories: List<ExpenseCategory>,
)

sealed interface ExpenseDetailEvent {
    data object Deleted : ExpenseDetailEvent
    data object DeleteFailed : ExpenseDetailEvent
}

@HiltViewModel
class ExpenseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: ExpenseRepository,
    private val deleteExpense: DeleteExpenseUseCase,
) : ViewModel() {

    val expenseId: String = savedStateHandle.toRoute<ExpenseDetailRoute>().expenseId

    private val _events = Channel<ExpenseDetailEvent>(Channel.BUFFERED)
    val events: Flow<ExpenseDetailEvent> = _events.receiveAsFlow()

    val state: StateFlow<ExpenseDetailUiState> = combine(
        repository.observeExpense(expenseId),
        repository.observeCategories(includeInactive = true),
        repository.observeProducts(null, includeInactive = true).map { products -> products.associate { it.id to it.categoryId } },
    ) { details, categories, productCategories ->
        val used = details?.items?.mapNotNull { item -> item.productId?.let(productCategories::get) }.orEmpty().toSet()
        ExpenseDetailUiState(loading = false, details = details, categories = categories.filter { it.id in used })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpenseDetailUiState(true, null, emptyList()))

    fun delete() {
        viewModelScope.launch {
            runCatching { deleteExpense(expenseId) }
                .onSuccess { _events.send(ExpenseDetailEvent.Deleted) }
                .onFailure { _events.send(ExpenseDetailEvent.DeleteFailed) }
        }
    }
}

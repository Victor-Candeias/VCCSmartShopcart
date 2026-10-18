package pt.vcc.smartshopcart.feature.shoppinglist

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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.vcc.smartshopcart.core.money.LineTotalCalculator
import pt.vcc.smartshopcart.core.navigation.ListDetailRoute
import pt.vcc.smartshopcart.domain.model.MeasureUnit
import pt.vcc.smartshopcart.domain.model.SessionMode
import pt.vcc.smartshopcart.domain.model.ShoppingList
import pt.vcc.smartshopcart.domain.model.ShoppingListItem
import pt.vcc.smartshopcart.domain.model.Store
import pt.vcc.smartshopcart.domain.repository.ShoppingListRepository
import pt.vcc.smartshopcart.domain.repository.ShoppingSessionRepository
import pt.vcc.smartshopcart.domain.repository.StoreRepository
import pt.vcc.smartshopcart.domain.usecase.DeleteShoppingListItemUseCase
import pt.vcc.smartshopcart.domain.usecase.DeleteShoppingListUseCase
import pt.vcc.smartshopcart.domain.usecase.DuplicateShoppingListUseCase
import pt.vcc.smartshopcart.domain.usecase.ListItemInput
import pt.vcc.smartshopcart.domain.usecase.SaveShoppingListItemUseCase
import pt.vcc.smartshopcart.domain.usecase.SetShoppingListArchivedUseCase
import pt.vcc.smartshopcart.domain.usecase.StartPlannedSessionUseCase
import pt.vcc.smartshopcart.domain.usecase.StartSessionResult
import pt.vcc.smartshopcart.domain.usecase.UpdateShoppingListUseCase
import java.math.BigDecimal
import javax.inject.Inject

data class ListDetailUiState(
    val loading: Boolean = true,
    val list: ShoppingList? = null,
    val items: List<ShoppingListItem> = emptyList(),
    /** Soma das estimativas disponíveis e número de itens sem estimativa. */
    val estimatedTotalCents: Long = 0,
    val itemsWithoutEstimate: Int = 0,
    val hasActiveSession: Boolean = false,
)

sealed interface ListDetailEvent {
    data class OpenList(val listId: String) : ListDetailEvent
    data class OpenSession(val sessionId: String, val mode: SessionMode) : ListDetailEvent
    data object ActiveSessionExists : ListDetailEvent
    data object Closed : ListDetailEvent
}

@HiltViewModel
class ListDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    lists: ShoppingListRepository,
    sessions: ShoppingSessionRepository,
    stores: StoreRepository,
    private val updateList: UpdateShoppingListUseCase,
    private val setArchivedUseCase: SetShoppingListArchivedUseCase,
    private val duplicateList: DuplicateShoppingListUseCase,
    private val deleteList: DeleteShoppingListUseCase,
    private val saveItemUseCase: SaveShoppingListItemUseCase,
    private val deleteItemUseCase: DeleteShoppingListItemUseCase,
    private val startPlanned: StartPlannedSessionUseCase,
) : ViewModel() {

    val listId: String = savedStateHandle.toRoute<ListDetailRoute>().listId

    val state: StateFlow<ListDetailUiState> = combine(
        lists.observeList(listId),
        sessions.observeActiveSession(),
    ) { details, active ->
        val items = details?.items.orEmpty()
        ListDetailUiState(
            loading = false,
            list = details?.list,
            items = items,
            estimatedTotalCents = LineTotalCalculator.sum(
                items.mapNotNull { item ->
                    item.estimatedPriceCents?.let { LineTotalCalculator.lineTotalCents(it, item.plannedQuantity, item.unit) }
                },
            ),
            itemsWithoutEstimate = items.count { it.estimatedPriceCents == null },
            hasActiveSession = active != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListDetailUiState())

    val stores: StateFlow<List<Store>> = stores.observeStores()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<ListDetailEvent>(Channel.BUFFERED)
    val events: Flow<ListDetailEvent> = _events.receiveAsFlow()

    fun rename(name: String, budgetCents: Long?) = launch { updateList(listId, name, budgetCents) }

    fun saveItem(itemId: String?, name: String, quantity: BigDecimal, unit: MeasureUnit, estimatedCents: Long?) =
        launch { saveItemUseCase(listId, itemId, ListItemInput(name, quantity, unit, estimatedCents)) }

    fun deleteItem(itemId: String) = launch { deleteItemUseCase(itemId) }

    fun setArchived(archived: Boolean) = launch { setArchivedUseCase(listId, archived) }

    fun duplicate(newName: String) = launch {
        _events.send(ListDetailEvent.OpenList(duplicateList(listId, newName).id))
    }

    fun delete() = launch {
        deleteList(listId)
        _events.send(ListDetailEvent.Closed)
    }

    fun startShopping(storeName: String?, budgetCents: Long?) = launch {
        when (val result = startPlanned(listId, storeName, budgetCents)) {
            is StartSessionResult.Started -> _events.send(ListDetailEvent.OpenSession(result.session.id, result.session.mode))
            is StartSessionResult.ActiveSessionExists -> _events.send(ListDetailEvent.ActiveSessionExists)
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

package pt.vcc.smartshopcart.feature.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.vcc.smartshopcart.core.navigation.PlannedSessionRoute
import pt.vcc.smartshopcart.domain.model.SessionStatus
import pt.vcc.smartshopcart.domain.model.ShoppingListItem
import pt.vcc.smartshopcart.domain.model.ShoppingSessionItem
import pt.vcc.smartshopcart.domain.repository.SettingsRepository
import pt.vcc.smartshopcart.domain.repository.ShoppingListRepository
import pt.vcc.smartshopcart.domain.repository.ShoppingSessionRepository
import pt.vcc.smartshopcart.domain.usecase.AddSessionItemUseCase
import pt.vcc.smartshopcart.domain.usecase.BudgetLevel
import pt.vcc.smartshopcart.domain.usecase.BudgetStatus
import pt.vcc.smartshopcart.domain.usecase.CalculateRemainingBudgetUseCase
import pt.vcc.smartshopcart.domain.usecase.CalculateSessionTotalUseCase
import pt.vcc.smartshopcart.domain.usecase.CancelShoppingSessionUseCase
import pt.vcc.smartshopcart.domain.usecase.CompleteShoppingSessionUseCase
import pt.vcc.smartshopcart.domain.usecase.DerivePlannedProgressUseCase
import pt.vcc.smartshopcart.domain.usecase.NewSessionItem
import pt.vcc.smartshopcart.domain.usecase.PlannedProgress
import pt.vcc.smartshopcart.domain.usecase.RemoveSessionItemUseCase
import pt.vcc.smartshopcart.domain.usecase.RestoreSessionItemUseCase
import pt.vcc.smartshopcart.domain.usecase.SessionNotActiveException
import pt.vcc.smartshopcart.domain.usecase.SetPlannedItemSkippedUseCase
import java.math.BigDecimal
import javax.inject.Inject

data class PlannedSessionUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val isActive: Boolean = false,
    val listName: String? = null,
    val storeName: String? = null,
    val budget: BudgetStatus = BudgetStatus(0, null, null, BudgetLevel.NONE, 0f),
    val progress: PlannedProgress = PlannedProgress(emptyList(), emptyList()),
    val itemCount: Int = 0,
)

sealed interface PlannedSessionEvent {
    data class ItemRemoved(val item: ShoppingSessionItem) : PlannedSessionEvent
    data object SessionClosed : PlannedSessionEvent
    data object SessionNotActive : PlannedSessionEvent
}

@HiltViewModel
class PlannedSessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    sessions: ShoppingSessionRepository,
    lists: ShoppingListRepository,
    settings: SettingsRepository,
    private val addItem: AddSessionItemUseCase,
    private val removeItem: RemoveSessionItemUseCase,
    private val restoreItem: RestoreSessionItemUseCase,
    private val setSkippedUseCase: SetPlannedItemSkippedUseCase,
    private val completeSession: CompleteShoppingSessionUseCase,
    private val cancelSession: CancelShoppingSessionUseCase,
    private val calculateTotal: CalculateSessionTotalUseCase,
    private val calculateBudget: CalculateRemainingBudgetUseCase,
    private val deriveProgress: DerivePlannedProgressUseCase,
) : ViewModel() {

    val sessionId: String = savedStateHandle.toRoute<PlannedSessionRoute>().sessionId

    private val sessionFlow = sessions.observeSession(sessionId)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val listItemsFlow: Flow<List<ShoppingListItem>> = sessionFlow
        .map { it?.session?.shoppingListId }
        .distinctUntilChanged()
        .flatMapLatest { listId ->
            if (listId == null) flowOf(emptyList()) else lists.observeList(listId).map { it?.items.orEmpty() }
        }

    val uiState: StateFlow<PlannedSessionUiState> = combine(
        sessionFlow,
        listItemsFlow,
        sessions.observeSkippedListItems(sessionId),
        settings.settings,
    ) { details, listItems, skipped, appSettings ->
        if (details == null) {
            PlannedSessionUiState(loading = false, missing = true)
        } else {
            val total = calculateTotal(details.items)
            PlannedSessionUiState(
                loading = false,
                isActive = details.session.status == SessionStatus.ACTIVE,
                listName = details.listName,
                storeName = details.store?.name,
                budget = calculateBudget(
                    totalCents = details.session.finalTotalCents ?: total,
                    budgetCents = details.session.budgetCents,
                    warningPercent = appSettings.budgetWarningPercent,
                ),
                progress = deriveProgress(listItems, details.items, skipped),
                itemCount = details.items.size,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlannedSessionUiState())

    private val _events = Channel<PlannedSessionEvent>(Channel.BUFFERED)
    val events: Flow<PlannedSessionEvent> = _events.receiveAsFlow()

    fun addPlanned(listItem: ShoppingListItem, priceCents: Long, quantity: BigDecimal) = guarded {
        addItem(
            sessionId,
            NewSessionItem(
                unitPriceCents = priceCents,
                quantity = quantity,
                unit = listItem.unit,
                name = listItem.name,
                productId = listItem.productId,
                shoppingListItemId = listItem.id,
            ),
        )
    }

    fun addExtra(name: String?, priceCents: Long, quantity: BigDecimal) = guarded {
        addItem(sessionId, NewSessionItem(unitPriceCents = priceCents, quantity = quantity, name = name))
    }

    fun setSkipped(listItemId: String, skipped: Boolean) = guarded { setSkippedUseCase(sessionId, listItemId, skipped) }

    fun onRemove(item: ShoppingSessionItem) = guarded {
        removeItem(item.id)?.let { _events.send(PlannedSessionEvent.ItemRemoved(it)) }
    }

    fun onUndoRemove(item: ShoppingSessionItem) = guarded { restoreItem(item) }

    fun onComplete() = guarded {
        completeSession(sessionId)
        _events.send(PlannedSessionEvent.SessionClosed)
    }

    fun onCancelSession() = guarded {
        cancelSession(sessionId)
        _events.send(PlannedSessionEvent.SessionClosed)
    }

    private fun guarded(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (_: SessionNotActiveException) {
                _events.send(PlannedSessionEvent.SessionNotActive)
            }
        }
    }
}
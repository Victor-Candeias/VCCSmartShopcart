package pt.vcc.smartshopcart.feature.calculator

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
import pt.vcc.smartshopcart.core.money.MoneyParser
import pt.vcc.smartshopcart.core.navigation.CalculatorRoute
import pt.vcc.smartshopcart.core.ui.components.KeypadInput
import pt.vcc.smartshopcart.core.ui.components.KeypadKey
import pt.vcc.smartshopcart.domain.model.SessionStatus
import pt.vcc.smartshopcart.domain.model.ShoppingSessionItem
import pt.vcc.smartshopcart.domain.repository.SettingsRepository
import pt.vcc.smartshopcart.domain.repository.ShoppingSessionRepository
import pt.vcc.smartshopcart.domain.usecase.AddSessionItemUseCase
import pt.vcc.smartshopcart.domain.usecase.BudgetLevel
import pt.vcc.smartshopcart.domain.usecase.BudgetStatus
import pt.vcc.smartshopcart.domain.usecase.CalculateRemainingBudgetUseCase
import pt.vcc.smartshopcart.domain.usecase.CalculateSessionTotalUseCase
import pt.vcc.smartshopcart.domain.usecase.CancelShoppingSessionUseCase
import pt.vcc.smartshopcart.domain.usecase.CompleteShoppingSessionUseCase
import pt.vcc.smartshopcart.domain.usecase.NewSessionItem
import pt.vcc.smartshopcart.domain.usecase.RemoveSessionItemUseCase
import pt.vcc.smartshopcart.domain.usecase.RestoreSessionItemUseCase
import pt.vcc.smartshopcart.domain.usecase.SessionNotActiveException
import javax.inject.Inject

data class CalculatorUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val storeName: String? = null,
    val isActive: Boolean = false,
    val items: List<ShoppingSessionItem> = emptyList(),
    val budget: BudgetStatus = BudgetStatus(0, null, null, BudgetLevel.NONE, 0f),
    val entry: String = "",
) {
    val entryCents: Long? get() = MoneyParser.parseToCents(entry.removeSuffix(","))?.takeIf { it > 0 }
}

sealed interface CalculatorEvent {
    data class ItemRemoved(val item: ShoppingSessionItem) : CalculatorEvent
    data object SessionClosed : CalculatorEvent
    data object SessionNotActive : CalculatorEvent
}

@HiltViewModel
class CalculatorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    sessions: ShoppingSessionRepository,
    settings: SettingsRepository,
    private val addItem: AddSessionItemUseCase,
    private val removeItem: RemoveSessionItemUseCase,
    private val restoreItem: RestoreSessionItemUseCase,
    private val completeSession: CompleteShoppingSessionUseCase,
    private val cancelSession: CancelShoppingSessionUseCase,
    private val calculateTotal: CalculateSessionTotalUseCase,
    private val calculateBudget: CalculateRemainingBudgetUseCase,
) : ViewModel() {

    val sessionId: String = savedStateHandle.toRoute<CalculatorRoute>().sessionId

    private val _events = Channel<CalculatorEvent>(Channel.BUFFERED)
    val events: Flow<CalculatorEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<CalculatorUiState> = combine(
        sessions.observeSession(sessionId),
        settings.settings,
        savedStateHandle.getStateFlow(KEY_ENTRY, ""),
    ) { details, appSettings, entry ->
        if (details == null) {
            CalculatorUiState(loading = false, missing = true)
        } else {
            val total = calculateTotal(details.items)
            CalculatorUiState(
                loading = false,
                storeName = details.store?.name,
                isActive = details.session.status == SessionStatus.ACTIVE,
                items = details.items,
                budget = calculateBudget(
                    totalCents = details.session.finalTotalCents ?: total,
                    budgetCents = details.session.budgetCents,
                    warningPercent = appSettings.budgetWarningPercent,
                ),
                entry = entry,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalculatorUiState())

    fun onKey(key: KeypadKey) {
        savedStateHandle[KEY_ENTRY] = KeypadInput.apply(savedStateHandle[KEY_ENTRY] ?: "", key)
    }

    fun onAdd() {
        val cents = uiState.value.entryCents ?: return
        guarded {
            addItem(sessionId, NewSessionItem(unitPriceCents = cents))
            savedStateHandle[KEY_ENTRY] = ""
        }
    }

    fun onRemove(item: ShoppingSessionItem) = guarded {
        removeItem(item.id)?.let { _events.send(CalculatorEvent.ItemRemoved(it)) }
    }

    fun onUndoRemove(item: ShoppingSessionItem) = guarded { restoreItem(item) }

    fun onComplete() = guarded {
        completeSession(sessionId)
        _events.send(CalculatorEvent.SessionClosed)
    }

    fun onCancelSession() = guarded {
        cancelSession(sessionId)
        _events.send(CalculatorEvent.SessionClosed)
    }

    private fun guarded(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (_: SessionNotActiveException) {
                _events.send(CalculatorEvent.SessionNotActive)
            }
        }
    }

    private companion object {
        const val KEY_ENTRY = "entry"
    }
}

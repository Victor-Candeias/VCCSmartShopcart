package pt.vcc.smartshopcart.feature.history

import android.database.SQLException
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
import pt.vcc.smartshopcart.core.navigation.HistoryDetailRoute
import pt.vcc.smartshopcart.domain.model.ShoppingSession
import pt.vcc.smartshopcart.domain.model.ShoppingSessionItem
import pt.vcc.smartshopcart.domain.repository.SettingsRepository
import pt.vcc.smartshopcart.domain.repository.ShoppingSessionRepository
import pt.vcc.smartshopcart.domain.usecase.BudgetStatus
import pt.vcc.smartshopcart.domain.usecase.CalculateRemainingBudgetUseCase
import pt.vcc.smartshopcart.domain.usecase.DeletePurchaseUseCase
import pt.vcc.smartshopcart.domain.usecase.ObservePriceHistoryUseCase
import pt.vcc.smartshopcart.domain.usecase.StorePriceSummary
import javax.inject.Inject

data class HistoryDetailUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val session: ShoppingSession? = null,
    val storeName: String? = null,
    val listName: String? = null,
    val items: List<ShoppingSessionItem> = emptyList(),
    val budget: BudgetStatus? = null,
)

/** Histórico de preços do artigo escolhido; [prices] é `null` enquanto carrega. */
data class PriceHistoryUiState(
    val item: ShoppingSessionItem,
    val prices: List<StorePriceSummary>?,
)

sealed interface HistoryDetailEvent {
    data object Deleted : HistoryDetailEvent
    data object DeleteFailed : HistoryDetailEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    sessions: ShoppingSessionRepository,
    settings: SettingsRepository,
    calculateBudget: CalculateRemainingBudgetUseCase,
    observePriceHistory: ObservePriceHistoryUseCase,
    private val deletePurchase: DeletePurchaseUseCase,
) : ViewModel() {

    private val sessionId = savedStateHandle.toRoute<HistoryDetailRoute>().sessionId

    private val _events = Channel<HistoryDetailEvent>(Channel.BUFFERED)
    val events: Flow<HistoryDetailEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<HistoryDetailUiState> = combine(
        sessions.observeSession(sessionId),
        settings.settings,
    ) { details, appSettings ->
        if (details == null) {
            HistoryDetailUiState(loading = false, missing = true)
        } else {
            val session = details.session
            HistoryDetailUiState(
                loading = false,
                session = session,
                storeName = details.store?.name,
                listName = details.listName,
                items = details.items,
                budget = calculateBudget(
                    totalCents = session.finalTotalCents ?: details.items.sumOf { it.totalCents },
                    budgetCents = session.budgetCents,
                    warningPercent = appSettings.budgetWarningPercent,
                ),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryDetailUiState())

    val priceHistory: StateFlow<PriceHistoryUiState?> = combine(
        savedStateHandle.getStateFlow<String?>(KEY_SELECTED, null),
        uiState,
    ) { selectedId, state -> state.items.firstOrNull { it.id == selectedId && it.barcode != null } }
        .distinctUntilChanged()
        .flatMapLatest { item ->
            if (item == null) {
                flowOf(null)
            } else {
                observePriceHistory(item.barcode!!).map<List<StorePriceSummary>, PriceHistoryUiState?> {
                    PriceHistoryUiState(item, it)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onShowPrices(item: ShoppingSessionItem) {
        if (item.barcode != null) savedStateHandle[KEY_SELECTED] = item.id
    }

    fun onHidePrices() {
        savedStateHandle[KEY_SELECTED] = null
    }

    fun onDelete() {
        viewModelScope.launch {
            val deleted = try {
                deletePurchase(sessionId)
            } catch (_: SQLException) {
                false
            }
            _events.send(if (deleted) HistoryDetailEvent.Deleted else HistoryDetailEvent.DeleteFailed)
        }
    }

    private companion object {
        const val KEY_SELECTED = "selected_item"
    }
}

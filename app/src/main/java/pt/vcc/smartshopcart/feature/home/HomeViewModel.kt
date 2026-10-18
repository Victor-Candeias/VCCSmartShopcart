package pt.vcc.smartshopcart.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.vcc.smartshopcart.core.money.LineTotalCalculator
import pt.vcc.smartshopcart.domain.model.SessionMode
import pt.vcc.smartshopcart.domain.model.Store
import pt.vcc.smartshopcart.domain.repository.ShoppingSessionRepository
import pt.vcc.smartshopcart.domain.repository.StoreRepository
import pt.vcc.smartshopcart.domain.usecase.StartSessionResult
import pt.vcc.smartshopcart.domain.usecase.StartShoppingSessionUseCase
import javax.inject.Inject

data class ActiveSessionUi(
    val sessionId: String,
    val mode: SessionMode,
    val storeName: String?,
    val listName: String?,
    val itemCount: Int,
    val totalCents: Long,
)

sealed interface HomeEvent {
    data class OpenSession(val sessionId: String, val mode: SessionMode) : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    sessions: ShoppingSessionRepository,
    stores: StoreRepository,
    private val startSession: StartShoppingSessionUseCase,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeSession: StateFlow<ActiveSessionUi?> = sessions.observeActiveSession()
        .flatMapLatest { active ->
            if (active == null) flowOf(null) else sessions.observeSession(active.id)
        }
        .map { details ->
            details?.let {
                ActiveSessionUi(
                    sessionId = it.session.id,
                    mode = it.session.mode,
                    storeName = it.store?.name,
                    listName = it.listName,
                    itemCount = it.items.size,
                    totalCents = LineTotalCalculator.sum(it.items.map { item -> item.totalCents }),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val stores: StateFlow<List<Store>> = stores.observeStores()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    fun startCalculator(storeName: String?, budgetCents: Long?) {
        viewModelScope.launch {
            val session = when (val result = startSession(SessionMode.CALCULATOR, null, storeName, budgetCents)) {
                is StartSessionResult.Started -> result.session
                is StartSessionResult.ActiveSessionExists -> result.session
            }
            _events.send(HomeEvent.OpenSession(session.id, session.mode))
        }
    }
}

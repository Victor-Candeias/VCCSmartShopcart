package pt.vcc.smartshopcart.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import pt.vcc.smartshopcart.domain.repository.SessionSummary
import pt.vcc.smartshopcart.domain.repository.ShoppingSessionRepository
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    sessions: ShoppingSessionRepository,
) : ViewModel() {
    /** `null` enquanto carrega; compras concluídas, mais recente primeiro. */
    val purchases: StateFlow<List<SessionSummary>?> = sessions.observeCompletedSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

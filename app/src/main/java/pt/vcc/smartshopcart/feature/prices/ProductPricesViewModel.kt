package pt.vcc.smartshopcart.feature.prices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.vcc.smartshopcart.core.common.TimeProvider
import pt.vcc.smartshopcart.domain.model.PriceBasis
import pt.vcc.smartshopcart.domain.model.ReferencePrice
import pt.vcc.smartshopcart.domain.model.ReferencePrices
import pt.vcc.smartshopcart.domain.model.ReferenceSyncState
import pt.vcc.smartshopcart.domain.repository.ReferencePriceRepository
import pt.vcc.smartshopcart.domain.repository.ReferenceRefreshResult
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Aviso discreto sobre a última tentativa de atualização; a cache continua visível (OP10, OP12). */
enum class PricesNotice { OFFLINE, FAILED, RATE_LIMITED }

data class ProductPricesUiState(
    val barcode: String? = null,
    /** Preços por embalagem e por kg ficam em listas separadas e nunca se comparam (OP7). */
    val unitPrices: List<ReferencePrice> = emptyList(),
    val kilogramPrices: List<ReferencePrice> = emptyList(),
    val sync: ReferenceSyncState? = null,
    val refreshing: Boolean = false,
    val notice: PricesNotice? = null,
    val today: LocalDate = LocalDate.now(),
) {
    val isEmpty: Boolean get() = unitPrices.isEmpty() && kilogramPrices.isEmpty()

    /** Ainda não houve nenhuma consulta concluída para este EAN. */
    val neverFetched: Boolean get() = sync?.lastSuccessAt == null
}

/** Preços de referência Open Prices de um EAN (§11): cache Room imediata e atualização conforme a política OP10. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProductPricesViewModel @Inject constructor(
    private val repository: ReferencePriceRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val barcode = MutableStateFlow<String?>(null)
    private val refreshing = MutableStateFlow(false)
    private val notice = MutableStateFlow<PricesNotice?>(null)
    private var refreshJob: Job? = null
    private var refreshGeneration = 0

    private val cached = barcode.flatMapLatest { code ->
        if (code == null) flowOf(null) else repository.observe(code).map { code to it }
    }

    val state: StateFlow<ProductPricesUiState> = combine(cached, refreshing, notice) { data, loading, message ->
        toState(data?.first, data?.second, loading, message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProductPricesUiState(today = today()))

    /** Mostra a cache do EAN e atualiza-a se tiver expirado; o mesmo EAN não gera chamadas repetidas (OP13). */
    fun load(code: String) {
        if (barcode.value == code && refreshJob?.isActive == true) return
        if (barcode.value != code) {
            refreshJob?.cancel()
            notice.value = null
            barcode.value = code
        }
        refresh(force = false)
    }

    fun retry() = refresh(force = true)

    private fun refresh(force: Boolean) {
        val code = barcode.value ?: return
        refreshJob?.cancel()
        val generation = ++refreshGeneration
        refreshJob = viewModelScope.launch {
            refreshing.value = true
            try {
                notice.value = when (repository.refresh(code, force)) {
                    ReferenceRefreshResult.Offline -> PricesNotice.OFFLINE
                    ReferenceRefreshResult.Failed -> PricesNotice.FAILED
                    is ReferenceRefreshResult.RateLimited -> PricesNotice.RATE_LIMITED
                    ReferenceRefreshResult.UpToDate,
                    ReferenceRefreshResult.Unsupported,
                    is ReferenceRefreshResult.Updated,
                    -> null
                }
            } finally {
                // Uma atualização cancelada não pode esconder o progresso da que a substituiu.
                if (generation == refreshGeneration) refreshing.value = false
            }
        }
    }

    private fun toState(code: String?, data: ReferencePrices?, loading: Boolean, message: PricesNotice?): ProductPricesUiState {
        val prices = data?.prices.orEmpty()
        return ProductPricesUiState(
            barcode = code,
            unitPrices = prices.filter { it.basis == PriceBasis.UNIT },
            kilogramPrices = prices.filter { it.basis == PriceBasis.KILOGRAM },
            sync = data?.sync,
            refreshing = loading,
            notice = message,
            today = today(),
        )
    }

    private fun today(): LocalDate = Instant.ofEpochMilli(time.now()).atZone(ZoneId.systemDefault()).toLocalDate()
}

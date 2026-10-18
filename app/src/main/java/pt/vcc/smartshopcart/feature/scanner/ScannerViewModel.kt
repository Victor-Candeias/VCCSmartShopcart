package pt.vcc.smartshopcart.feature.scanner

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.vcc.smartshopcart.core.money.LineTotalCalculator
import pt.vcc.smartshopcart.core.money.MoneyFormatter
import pt.vcc.smartshopcart.core.money.MoneyParser
import pt.vcc.smartshopcart.core.money.QuantityParser
import pt.vcc.smartshopcart.core.navigation.ScannerRoute
import pt.vcc.smartshopcart.domain.model.MeasureUnit
import pt.vcc.smartshopcart.domain.model.SessionStatus
import pt.vcc.smartshopcart.domain.model.ShoppingListItem
import pt.vcc.smartshopcart.domain.repository.ShoppingListRepository
import pt.vcc.smartshopcart.domain.repository.ShoppingSessionRepository
import pt.vcc.smartshopcart.domain.usecase.AddScannedItemUseCase
import pt.vcc.smartshopcart.domain.usecase.ProductLookupStatus
import pt.vcc.smartshopcart.domain.usecase.ResolveProductResult
import pt.vcc.smartshopcart.domain.usecase.ResolveProductUseCase
import pt.vcc.smartshopcart.domain.usecase.ScannedItemInput
import pt.vcc.smartshopcart.domain.usecase.SessionNotActiveException
import java.io.Serializable
import java.math.BigDecimal
import javax.inject.Inject

/** Rascunho do passo de confirmação (D25); sobrevive à recriação do processo via SavedStateHandle. */
data class ScanDraft(
    val barcode: String,
    val status: ProductLookupStatus,
    val productId: String? = null,
    val brand: String? = null,
    val quantityLabel: String? = null,
    val name: String = "",
    val price: String = "",
    val quantity: String = "1",
    val unit: MeasureUnit = MeasureUnit.UN,
    val suggestedPriceCents: Long? = null,
    val suggestedStoreName: String? = null,
) : Serializable {
    val priceCents: Long? get() = MoneyParser.parseToCents(price)?.takeIf { it > 0 }
    val quantityValue: BigDecimal? get() = QuantityParser.parse(quantity)
    val totalCents: Long?
        get() {
            val p = priceCents ?: return null
            val q = quantityValue ?: return null
            return LineTotalCalculator.lineTotalCents(p, q, unit)
        }
}

enum class ScanPhase { SCANNING, RESOLVING, CONFIRM }

data class ScannerUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val inactive: Boolean = false,
    val listItemName: String? = null,
    val phase: ScanPhase = ScanPhase.SCANNING,
    val multipleCodes: Boolean = false,
    val invalidCode: String? = null,
    val draft: ScanDraft? = null,
    val saving: Boolean = false,
)

sealed interface ScannerEvent {
    data object Added : ScannerEvent
    data object SessionNotActive : ScannerEvent
}

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val sessions: ShoppingSessionRepository,
    private val lists: ShoppingListRepository,
    private val resolveProduct: ResolveProductUseCase,
    private val addScannedItem: AddScannedItemUseCase,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ScannerRoute>()
    private val debouncer = ScanDebouncer()
    private var storeId: String? = null
    private var listItem: ShoppingListItem? = null

    private val _state = MutableStateFlow(ScannerUiState())
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()

    private val _events = Channel<ScannerEvent>(Channel.BUFFERED)
    val events: Flow<ScannerEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val session = sessions.getSession(route.sessionId)
            if (session == null) {
                _state.value = ScannerUiState(loading = false, missing = true)
                return@launch
            }
            storeId = session.storeId
            listItem = route.listItemId?.let { lists.getItem(it) }
            val draft = savedStateHandle.get<ScanDraft>(KEY_DRAFT)
            _state.value = ScannerUiState(
                loading = false,
                inactive = session.status != SessionStatus.ACTIVE,
                listItemName = listItem?.name,
                phase = if (draft != null) ScanPhase.CONFIRM else ScanPhase.SCANNING,
                draft = draft,
            )
        }
    }

    /** Valores lidos pela câmara num frame. */
    fun onCodesDetected(codes: List<String>) {
        val current = _state.value
        if (current.loading || current.phase != ScanPhase.SCANNING) return
        if (codes.size > 1) {
            _state.update { it.copy(multipleCodes = true) }
            return
        }
        val code = codes.singleOrNull() ?: return
        if (!debouncer.accept(code, SystemClock.elapsedRealtime())) return
        resolve(code, manual = false)
    }

    fun onManualCode(code: String) {
        if (_state.value.phase != ScanPhase.SCANNING) return
        resolve(code.filter(Char::isDigit), manual = true)
    }

    fun onDismissInvalidCode() = _state.update { it.copy(invalidCode = null) }

    fun onDraftChange(transform: (ScanDraft) -> ScanDraft) {
        val draft = _state.value.draft ?: return
        val updated = transform(draft)
        _state.update { it.copy(draft = updated) }
        savedStateHandle[KEY_DRAFT] = updated
    }

    /** Descarta o rascunho e volta à câmara; o mesmo código continua ignorado durante 3 s (D22). */
    fun onScanAnother() {
        _state.value.draft?.let { debouncer.mark(it.barcode, SystemClock.elapsedRealtime()) }
        savedStateHandle.remove<ScanDraft>(KEY_DRAFT)
        _state.update { it.copy(phase = ScanPhase.SCANNING, draft = null, multipleCodes = false) }
    }

    fun onConfirm() {
        val draft = _state.value.draft ?: return
        val price = draft.priceCents ?: return
        val quantity = draft.quantityValue ?: return
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                addScannedItem(
                    route.sessionId,
                    ScannedItemInput(
                        barcode = draft.barcode,
                        productId = draft.productId,
                        name = draft.name,
                        quantity = quantity,
                        unit = draft.unit,
                        unitPriceCents = price,
                        suggestedPriceCents = draft.suggestedPriceCents,
                        shoppingListItemId = route.listItemId,
                    ),
                )
                savedStateHandle.remove<ScanDraft>(KEY_DRAFT)
                _events.send(ScannerEvent.Added)
            } catch (_: SessionNotActiveException) {
                _state.update { it.copy(saving = false, inactive = true) }
                _events.send(ScannerEvent.SessionNotActive)
            }
        }
    }

    private fun resolve(code: String, manual: Boolean) {
        _state.update { it.copy(phase = ScanPhase.RESOLVING, multipleCodes = false, invalidCode = null) }
        viewModelScope.launch {
            when (val result = resolveProduct(code, storeId)) {
                ResolveProductResult.InvalidBarcode -> _state.update {
                    it.copy(phase = ScanPhase.SCANNING, invalidCode = if (manual) code else null)
                }
                is ResolveProductResult.Resolved -> {
                    val draft = buildDraft(result)
                    savedStateHandle[KEY_DRAFT] = draft
                    _state.update { it.copy(phase = ScanPhase.CONFIRM, draft = draft) }
                }
            }
        }
    }

    private fun buildDraft(result: ResolveProductResult.Resolved): ScanDraft {
        val planned = listItem
        val suggestion = result.suggestedPrice
        val unit = planned?.unit ?: suggestion?.unit ?: MeasureUnit.UN
        // Só sugere o último preço se for comparável (mesma unidade de preço).
        val usableSuggestion = suggestion?.takeIf { it.unit.priceUnit == unit.priceUnit }
        val priceCents = usableSuggestion?.priceCents ?: planned?.estimatedPriceCents
        return ScanDraft(
            barcode = result.barcode,
            status = result.status,
            productId = result.product?.id,
            brand = result.product?.brand,
            quantityLabel = result.product?.quantityLabel,
            name = result.product?.name ?: planned?.name.orEmpty(),
            price = priceCents?.let(MoneyFormatter::formatPlain).orEmpty(),
            quantity = QuantityParser.format(planned?.plannedQuantity ?: BigDecimal.ONE),
            unit = unit,
            suggestedPriceCents = usableSuggestion?.priceCents,
            suggestedStoreName = usableSuggestion?.storeName,
        )
    }

    private companion object {
        const val KEY_DRAFT = "scan_draft"
    }
}

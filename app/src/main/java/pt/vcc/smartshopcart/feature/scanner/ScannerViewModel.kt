package pt.vcc.smartshopcart.feature.scanner

import android.database.SQLException
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
import pt.vcc.smartshopcart.core.navigation.ScannerRoute
import pt.vcc.smartshopcart.core.ocr.OcrLine
import pt.vcc.smartshopcart.core.ocr.PriceCandidateParser
import pt.vcc.smartshopcart.domain.model.SessionStatus
import pt.vcc.smartshopcart.domain.model.ShoppingListItem
import pt.vcc.smartshopcart.domain.repository.ShoppingListRepository
import pt.vcc.smartshopcart.domain.repository.ShoppingSessionRepository
import pt.vcc.smartshopcart.domain.usecase.CaptureConfirmation
import pt.vcc.smartshopcart.domain.usecase.ConfirmCaptureUseCase
import pt.vcc.smartshopcart.domain.usecase.OcrCapture
import pt.vcc.smartshopcart.domain.usecase.ResolveProductResult
import pt.vcc.smartshopcart.domain.usecase.ResolveProductUseCase
import pt.vcc.smartshopcart.domain.usecase.SessionNotActiveException
import javax.inject.Inject

enum class ScanPhase { SCANNING, RESOLVING, CONFIRM }

data class ScannerUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val inactive: Boolean = false,
    val listItemName: String? = null,
    val phase: ScanPhase = ScanPhase.SCANNING,
    val multipleCodes: Boolean = false,
    val invalidCode: String? = null,
    /** A fotografia não tinha código nem preço legível. */
    val nothingRecognized: Boolean = false,
    /** Uma fotografia está a ser processada. */
    val recognizing: Boolean = false,
    val draft: ScanDraft? = null,
    val saving: Boolean = false,
)

sealed interface ScannerEvent {
    data object Added : ScannerEvent
    data object SessionNotActive : ScannerEvent
    data object StorageError : ScannerEvent
}

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val sessions: ShoppingSessionRepository,
    private val lists: ShoppingListRepository,
    private val resolveProduct: ResolveProductUseCase,
    private val confirmCapture: ConfirmCaptureUseCase,
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

    /** Valores lidos pela câmara em tempo real (só enquanto ainda não há produto no rascunho). */
    fun onCodesDetected(codes: List<String>) {
        val current = _state.value
        if (current.loading || current.phase != ScanPhase.SCANNING || current.recognizing) return
        if (current.draft?.barcode != null) return
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

    fun onPhotoStarted() = _state.update { it.copy(recognizing = true, nothingRecognized = false, multipleCodes = false) }

    fun onPhotoFailed() = _state.update { it.copy(recognizing = false, nothingRecognized = true) }

    /**
     * Resultado de uma fotografia (§5.1 A): código e texto lidos da mesma imagem. Junta-se ao rascunho
     * existente (§5.1 B); nada é adicionado sem confirmação.
     */
    fun onPhotoRecognized(barcodes: List<String>, lines: List<OcrLine>) {
        if (_state.value.phase != ScanPhase.SCANNING) return
        val codes = barcodes.distinct()
        if (codes.size > 1) {
            _state.update { it.copy(recognizing = false, multipleCodes = true) }
            return
        }
        val prices = PriceCandidateParser.parse(lines)
        viewModelScope.launch {
            var draft = _state.value.draft
            var productFound = false
            codes.singleOrNull()?.let { code ->
                val result = resolveProduct(code, storeId)
                if (result is ResolveProductResult.Resolved) {
                    draft = CaptureDraftReducer.withProduct(draft, result, listItem)
                    productFound = true
                }
            }
            val withPrices = CaptureDraftReducer.withPrices(draft, prices, listItem)
            if (withPrices == null && !productFound) {
                _state.update { it.copy(recognizing = false, nothingRecognized = true) }
                return@launch
            }
            showDraft(withPrices ?: draft!!)
        }
    }

    fun onDismissMessage() = _state.update { it.copy(invalidCode = null, nothingRecognized = false) }

    fun onDraftChange(transform: (ScanDraft) -> ScanDraft) {
        val draft = _state.value.draft ?: return
        val updated = transform(draft)
        _state.update { it.copy(draft = updated) }
        savedStateHandle[KEY_DRAFT] = updated
    }

    fun onSelectCandidate(index: Int) = onDraftChange { CaptureDraftReducer.selectCandidate(it, index) }

    /** Volta à câmara mantendo o rascunho, para ler o código ou fotografar o preço em falta. */
    fun onCaptureMore() {
        if (_state.value.draft == null) return
        _state.update { it.copy(phase = ScanPhase.SCANNING, multipleCodes = false, nothingRecognized = false) }
    }

    /** Volta à confirmação sem mais capturas. */
    fun onReviewDraft() {
        if (_state.value.draft == null) return
        _state.update { it.copy(phase = ScanPhase.CONFIRM, multipleCodes = false, nothingRecognized = false) }
    }

    /** Descarta o rascunho e volta à câmara; o mesmo código continua ignorado durante 3 s (D22). */
    fun onScanAnother() {
        _state.value.draft?.barcode?.let { debouncer.mark(it, SystemClock.elapsedRealtime()) }
        savedStateHandle.remove<ScanDraft>(KEY_DRAFT)
        _state.update {
            it.copy(phase = ScanPhase.SCANNING, draft = null, multipleCodes = false, nothingRecognized = false)
        }
    }

    fun onConfirm() {
        val draft = _state.value.draft ?: return
        val price = draft.priceCents ?: return
        val quantity = draft.quantityValue ?: return
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                confirmCapture(
                    route.sessionId,
                    CaptureConfirmation(
                        barcode = draft.barcode,
                        productId = draft.productId,
                        name = draft.name,
                        quantity = quantity,
                        unit = draft.unit,
                        unitPriceCents = price,
                        suggestedPriceCents = draft.suggestedPriceCents,
                        ocr = draft.ocrRawText?.takeIf { draft.priceCandidates.isNotEmpty() }?.let {
                            OcrCapture(it, draft.priceCandidates, draft.ocrPriceCents)
                        },
                        shoppingListItemId = route.listItemId,
                    ),
                )
                savedStateHandle.remove<ScanDraft>(KEY_DRAFT)
                _events.send(ScannerEvent.Added)
            } catch (_: SessionNotActiveException) {
                _state.update { it.copy(saving = false, inactive = true) }
                _events.send(ScannerEvent.SessionNotActive)
            } catch (_: SQLException) {
                // O rascunho mantém-se para o utilizador poder tentar de novo.
                _state.update { it.copy(saving = false) }
                _events.send(ScannerEvent.StorageError)
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
                is ResolveProductResult.Resolved ->
                    showDraft(CaptureDraftReducer.withProduct(_state.value.draft, result, listItem))
            }
        }
    }

    private fun showDraft(draft: ScanDraft) {
        savedStateHandle[KEY_DRAFT] = draft
        _state.update {
            it.copy(
                phase = ScanPhase.CONFIRM,
                draft = draft,
                recognizing = false,
                multipleCodes = false,
                nothingRecognized = false,
            )
        }
    }
    private companion object {
        const val KEY_DRAFT = "scan_draft"
    }
}

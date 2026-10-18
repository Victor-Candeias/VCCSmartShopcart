package pt.vcc.smartshopcart.feature.product

import android.database.SQLException
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
import pt.vcc.smartshopcart.core.navigation.ItemEditRoute
import pt.vcc.smartshopcart.domain.model.MeasureUnit
import pt.vcc.smartshopcart.domain.model.ShoppingSessionItem
import pt.vcc.smartshopcart.domain.repository.ShoppingSessionRepository
import pt.vcc.smartshopcart.domain.usecase.RemoveSessionItemUseCase
import pt.vcc.smartshopcart.domain.usecase.SessionNotActiveException
import pt.vcc.smartshopcart.domain.usecase.UpdateSessionItemUseCase
import java.io.Serializable
import javax.inject.Inject

data class ItemForm(
    val name: String = "",
    val price: String = "",
    val quantity: String = "1",
    val unit: MeasureUnit = MeasureUnit.UN,
    val discount: String = "",
) : Serializable {
    val priceCents: Long? get() = MoneyParser.parseToCents(price)
    val quantityValue get() = QuantityParser.parse(quantity)
    val discountCents: Long? get() = if (discount.isBlank()) 0 else MoneyParser.parseToCents(discount)

    val priceError get() = priceCents == null
    val quantityError get() = quantityValue == null
    val discountError get() = discountCents == null

    val totalCents: Long?
        get() {
            val p = priceCents ?: return null
            val q = quantityValue ?: return null
            val d = discountCents ?: return null
            return LineTotalCalculator.lineTotalCents(p, q, unit, d)
        }

    companion object {
        fun from(item: ShoppingSessionItem) = ItemForm(
            name = item.name.orEmpty(),
            price = MoneyFormatter.formatPlain(item.unitPriceCents),
            quantity = QuantityParser.format(item.quantity),
            unit = item.unit,
            discount = if (item.discountCents > 0) MoneyFormatter.formatPlain(item.discountCents) else "",
        )
    }
}

data class ItemEditorUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val barcode: String? = null,
    val form: ItemForm = ItemForm(),
)

sealed interface ItemEditorEvent {
    data object Done : ItemEditorEvent
    data object SessionNotActive : ItemEditorEvent
    data object StorageError : ItemEditorEvent
}

@HiltViewModel
class ItemEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val sessions: ShoppingSessionRepository,
    private val updateItem: UpdateSessionItemUseCase,
    private val removeItem: RemoveSessionItemUseCase,
) : ViewModel() {

    private val itemId = savedStateHandle.toRoute<ItemEditRoute>().itemId
    private var original: ShoppingSessionItem? = null

    private val _state = MutableStateFlow(ItemEditorUiState())
    val state: StateFlow<ItemEditorUiState> = _state.asStateFlow()

    private val _events = Channel<ItemEditorEvent>(Channel.BUFFERED)
    val events: Flow<ItemEditorEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val item = sessions.getItem(itemId)
            original = item
            if (item == null) {
                _state.value = ItemEditorUiState(loading = false, missing = true)
            } else {
                val form = savedStateHandle.get<ItemForm>(KEY_FORM) ?: ItemForm.from(item)
                _state.value = ItemEditorUiState(loading = false, barcode = item.barcode, form = form)
            }
        }
    }

    fun onFormChange(transform: (ItemForm) -> ItemForm) {
        _state.update { it.copy(form = transform(it.form)) }
        savedStateHandle[KEY_FORM] = _state.value.form
    }

    fun onSave() {
        val item = original ?: return
        val form = _state.value.form
        val price = form.priceCents ?: return
        val quantity = form.quantityValue ?: return
        val discount = form.discountCents ?: return
        launchGuarded {
            updateItem(
                item.copy(
                    name = form.name,
                    unitPriceCents = price,
                    quantity = quantity,
                    unit = form.unit,
                    discountCents = discount,
                ),
            )
            _events.send(ItemEditorEvent.Done)
        }
    }

    fun onDelete() = launchGuarded {
        removeItem(itemId)
        _events.send(ItemEditorEvent.Done)
    }

    private fun launchGuarded(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (_: SessionNotActiveException) {
                _events.send(ItemEditorEvent.SessionNotActive)
            } catch (_: SQLException) {
                _events.send(ItemEditorEvent.StorageError)
            }
        }
    }

    private companion object {
        const val KEY_FORM = "form"
    }
}

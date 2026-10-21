package pt.vcc.smartshopcart.feature.expenses.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pt.vcc.smartshopcart.core.common.IdProvider
import pt.vcc.smartshopcart.core.navigation.ExpenseEditorRoute
import pt.vcc.smartshopcart.data.seed.ExpenseCatalogSeeder
import pt.vcc.smartshopcart.domain.model.expense.ExpenseCategory
import pt.vcc.smartshopcart.domain.model.expense.ExpenseEditorDraft
import pt.vcc.smartshopcart.domain.model.expense.ExpenseProduct
import pt.vcc.smartshopcart.domain.model.expense.PriceSuggestion
import pt.vcc.smartshopcart.domain.model.expense.PriceSuggestionSource
import pt.vcc.smartshopcart.domain.repository.ExpenseDraftStore
import pt.vcc.smartshopcart.domain.repository.ExpenseRepository
import pt.vcc.smartshopcart.domain.usecase.expense.ExpenseDateTime
import pt.vcc.smartshopcart.domain.usecase.expense.ExpenseSearch
import pt.vcc.smartshopcart.domain.usecase.expense.ExpenseValidation
import pt.vcc.smartshopcart.domain.usecase.expense.GetExpenseCatalogUseCase
import pt.vcc.smartshopcart.domain.usecase.expense.LoadExpenseDraftUseCase
import pt.vcc.smartshopcart.domain.usecase.expense.RepeatExpenseUseCase
import pt.vcc.smartshopcart.domain.usecase.expense.SaveExpenseProductUseCase
import pt.vcc.smartshopcart.domain.usecase.expense.SaveExpenseResult
import pt.vcc.smartshopcart.domain.usecase.expense.SaveExpenseUseCase
import pt.vcc.smartshopcart.domain.usecase.expense.StartExpenseDraftUseCase
import pt.vcc.smartshopcart.domain.usecase.expense.SuggestProductPriceUseCase
import javax.inject.Inject

data class ExpenseEditorUiState(
    val draft: ExpenseEditorDraft,
    val categories: List<ExpenseCategory>,
    /** Produtos do painel: categoria escolhida, pesquisa e favoritos primeiro. */
    val panelProducts: List<ExpenseProduct>,
    val query: String,
    /** Nome pesquisado sem produto correspondente no catálogo; permite usá-lo só nesta despesa ou guardá-lo. */
    val newProductName: String?,
    val establishmentSuggestions: List<String>,
    /** Erro da última tentativa de gravação. */
    val error: ExpenseValidation?,
)

sealed interface ExpenseEditorEvent {
    data class Saved(val totalCents: Long) : ExpenseEditorEvent
    data object Closed : ExpenseEditorEvent
    data object StorageError : ExpenseEditorEvent
}

@HiltViewModel
class ExpenseEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: ExpenseRepository,
    private val drafts: ExpenseDraftStore,
    private val ids: IdProvider,
    getCatalog: GetExpenseCatalogUseCase,
    private val seeder: ExpenseCatalogSeeder,
    private val startDraft: StartExpenseDraftUseCase,
    private val loadDraft: LoadExpenseDraftUseCase,
    private val repeatExpense: RepeatExpenseUseCase,
    private val saveExpense: SaveExpenseUseCase,
    private val saveProduct: SaveExpenseProductUseCase,
    private val suggestPrice: SuggestProductPriceUseCase,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ExpenseEditorRoute>()
    private val draft = MutableStateFlow<ExpenseEditorDraft?>(null)
    private val error = MutableStateFlow<ExpenseValidation?>(null)
    private val query = savedStateHandle.getStateFlow(KEY_QUERY, "")
    private val persistLock = Mutex()
    private var closed = false
    private var suggestionJob: Job? = null

    /** Rascunho guardado de uma edição anterior, à espera de "Continuar" ou "Descartar". */
    private val _pendingDraft = MutableStateFlow<ExpenseEditorDraft?>(null)
    val pendingDraft: StateFlow<ExpenseEditorDraft?> = _pendingDraft
    private var freshDraft: ExpenseEditorDraft? = null

    private val _events = Channel<ExpenseEditorEvent>(Channel.BUFFERED)
    val events: Flow<ExpenseEditorEvent> = _events.receiveAsFlow()

    val state: StateFlow<ExpenseEditorUiState?> = combine(
        draft,
        getCatalog(),
        repository.observeEstablishments(),
        query,
        error,
    ) { current, catalog, establishments, q, err ->
        current ?: return@combine null
        val typed = ExpenseSearch.normalize(current.establishmentName)
        ExpenseEditorUiState(
            draft = current,
            categories = catalog.categories,
            panelProducts = ExpenseSearch.filter(
                catalog.products,
                current.selectedCategoryId.takeIf { q.isBlank() },
                q,
                favoritesFirst = true,
            ),
            query = q,
            newProductName = q.trim().takeIf { it.isNotEmpty() && !ExpenseSearch.hasExactMatch(catalog.products, it) },
            establishmentSuggestions = establishments.map { it.name }
                .filter { name ->
                    val normalized = ExpenseSearch.normalize(name)
                    typed.isNotEmpty() && normalized.contains(typed) && normalized != typed
                }
                .take(MAX_SUGGESTIONS),
            error = err,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            runCatching { seeder.seedIfNeeded() }
            val stored = drafts.get()?.takeUnless { it.isBlank }
            val restarting = savedStateHandle.get<Boolean>(KEY_STARTED) == true
            val sameEdit = stored != null && route.expenseId != null && stored.editingExpenseId == route.expenseId
            when {
                (route.resumeDraft || restarting || sameEdit) && stored != null -> activate(stored)
                else -> {
                    val initial = runCatching { initialDraft() }.getOrNull()
                    if (initial == null) {
                        _events.send(ExpenseEditorEvent.Closed)
                    } else if (stored != null) {
                        freshDraft = initial
                        _pendingDraft.value = stored
                    } else {
                        activate(initial)
                    }
                }
            }
        }
    }

    /** Nova despesa, edição de uma existente (`expenseId`) ou repetição (`repeatFromId`). */
    private suspend fun initialDraft(): ExpenseEditorDraft? = when {
        route.expenseId != null -> loadDraft(route.expenseId)
        route.repeatFromId != null -> repeatExpense(route.repeatFromId)
        else -> startDraft(route.categoryId, route.productId)
    }

    private fun activate(value: ExpenseEditorDraft) {
        _pendingDraft.value = null
        savedStateHandle[KEY_STARTED] = true
        draft.value = value
        persist(value)
    }

    fun continuePendingDraft() {
        _pendingDraft.value?.let(::activate)
    }

    fun discardPendingDraft() {
        freshDraft?.let(::activate)
    }

    private fun update(transform: (ExpenseEditorDraft) -> ExpenseEditorDraft) {
        val current = draft.value ?: return
        val next = runCatching { transform(current) }.getOrElse { return }
        if (next == current) return
        draft.value = next
        error.value = null
        persist(next)
    }

    private fun persist(value: ExpenseEditorDraft) {
        viewModelScope.launch {
            persistLock.withLock {
                if (closed) return@withLock
                runCatching { if (value.isBlank) drafts.clear() else drafts.save(value) }
            }
        }
    }

    fun setEstablishment(name: String) {
        update { it.copy(establishmentName = name.take(MAX_TEXT)) }
        refreshSuggestions()
    }

    /** Recalcula os preços ainda sugeridos para o estabelecimento atual (com atraso enquanto se escreve). */
    private fun refreshSuggestions() {
        suggestionJob?.cancel()
        suggestionJob = viewModelScope.launch {
            delay(SUGGESTION_DELAY_MS)
            val current = draft.value ?: return@launch
            val suggestions = runCatching { suggestPrice.suggestionsFor(current) }.getOrNull() ?: return@launch
            if (suggestions.isNotEmpty()) update { it.applySuggestions(suggestions) }
        }
    }

    fun setNotes(text: String) = update { it.copy(notes = text.take(MAX_TEXT)) }

    fun setDate(utcDateMillis: Long) = update { it.copy(occurredAt = ExpenseDateTime.withDate(it.occurredAt, utcDateMillis)) }

    fun setTime(hour: Int, minute: Int) = update { it.copy(occurredAt = ExpenseDateTime.withTime(it.occurredAt, hour, minute)) }

    fun selectCategory(categoryId: String?) = update { it.copy(selectedCategoryId = categoryId) }

    fun setQuery(text: String) {
        savedStateHandle[KEY_QUERY] = text
    }

    fun addProduct(product: ExpenseProduct) {
        viewModelScope.launch {
            val place = draft.value?.establishmentName
            val suggestion = runCatching { suggestPrice(product.id, place) }
                .getOrDefault(PriceSuggestion(product.defaultPriceCents, PriceSuggestionSource.CATALOG))
            update { it.addProduct(product, ids.newId(), suggestion.cents, suggestion.source) }
        }
    }

    fun addTemporaryProduct(name: String, priceCents: Long?) {
        update { it.addCustomProduct(name, ids.newId(), priceCents) }
    }

    /** Usa o texto pesquisado como produto só desta despesa; devolve a chave da linha para pedir o preço. */
    fun addQueryAsTemporary(): String? {
        val name = query.value.trim().takeIf { it.isNotEmpty() } ?: return null
        val key = ids.newId()
        update { it.addCustomProduct(name, key, null) }
        if (draft.value?.lines?.any { it.key == key } != true) return null
        setQuery("")
        return key
    }

    fun changeQuantity(lineKey: String, delta: Int) = update { it.changeQuantity(lineKey, delta) }

    fun setPrice(lineKey: String, unitPriceCents: Long) = update { it.setPrice(lineKey, unitPriceCents) }

    fun removeLine(lineKey: String) = update { it.removeLine(lineKey) }

    /** Cria o produto no catálogo e acrescenta-o à despesa. */
    fun createProduct(categoryId: String, name: String, icon: String?, defaultPriceCents: Long?) {
        viewModelScope.launch {
            runCatching { saveProduct(null, categoryId, name, icon, defaultPriceCents) }
                .onSuccess { product ->
                    setQuery("")
                    addProduct(product)
                }
                .onFailure { _events.send(ExpenseEditorEvent.StorageError) }
        }
    }

    fun save() {
        val current = draft.value ?: return
        viewModelScope.launch {
            persistLock.withLock {
                closed = true
                val result = runCatching { saveExpense(current) }.getOrElse {
                    closed = false
                    _events.send(ExpenseEditorEvent.StorageError)
                    return@withLock
                }
                when (result) {
                    is SaveExpenseResult.Saved -> _events.send(ExpenseEditorEvent.Saved(result.totalCents))
                    is SaveExpenseResult.Invalid -> {
                        closed = false
                        error.value = result.reason
                    }
                }
            }
        }
    }

    /** Descarta o rascunho; uma despesa já guardada não é alterada. */
    fun cancel() {
        viewModelScope.launch {
            persistLock.withLock {
                closed = true
                runCatching { drafts.clear() }
            }
            _events.send(ExpenseEditorEvent.Closed)
        }
    }

    private companion object {
        const val KEY_QUERY = "query"
        const val KEY_STARTED = "started"
        const val MAX_SUGGESTIONS = 5
        const val MAX_TEXT = 120
        const val SUGGESTION_DELAY_MS = 400L
    }
}

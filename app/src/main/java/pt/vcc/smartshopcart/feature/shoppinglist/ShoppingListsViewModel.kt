package pt.vcc.smartshopcart.feature.shoppinglist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.vcc.smartshopcart.domain.model.ShoppingListSummary
import pt.vcc.smartshopcart.domain.repository.ShoppingListRepository
import pt.vcc.smartshopcart.domain.usecase.CreateShoppingListUseCase
import javax.inject.Inject

@HiltViewModel
class ShoppingListsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    lists: ShoppingListRepository,
    private val createList: CreateShoppingListUseCase,
) : ViewModel() {

    val showArchived: StateFlow<Boolean> = savedStateHandle.getStateFlow(KEY_ARCHIVED, false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val lists: StateFlow<List<ShoppingListSummary>?> = showArchived
        .flatMapLatest { lists.observeLists(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _openList = Channel<String>(Channel.BUFFERED)
    val openList: Flow<String> = _openList.receiveAsFlow()

    fun setShowArchived(archived: Boolean) {
        savedStateHandle[KEY_ARCHIVED] = archived
    }

    fun create(name: String, budgetCents: Long?) {
        viewModelScope.launch { _openList.send(createList(name, budgetCents).id) }
    }

    private companion object {
        const val KEY_ARCHIVED = "archived"
    }
}

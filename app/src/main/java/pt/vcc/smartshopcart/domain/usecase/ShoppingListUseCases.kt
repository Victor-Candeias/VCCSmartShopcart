package pt.vcc.smartshopcart.domain.usecase

import pt.vcc.smartshopcart.core.common.IdProvider
import pt.vcc.smartshopcart.core.common.TimeProvider
import pt.vcc.smartshopcart.domain.model.MeasureUnit
import pt.vcc.smartshopcart.domain.model.SessionMode
import pt.vcc.smartshopcart.domain.model.ShoppingList
import pt.vcc.smartshopcart.domain.model.ShoppingListItem
import pt.vcc.smartshopcart.domain.repository.ShoppingListRepository
import java.math.BigDecimal
import javax.inject.Inject

class CreateShoppingListUseCase @Inject constructor(
    private val lists: ShoppingListRepository,
    private val ids: IdProvider,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(name: String, budgetCents: Long?): ShoppingList {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Nome vazio" }
        val now = time.now()
        val list = ShoppingList(ids.newId(), trimmed, budgetCents?.takeIf { it > 0 }, now, now, archived = false)
        lists.upsertList(list)
        return list
    }
}

class UpdateShoppingListUseCase @Inject constructor(
    private val lists: ShoppingListRepository,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(id: String, name: String, budgetCents: Long?) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Nome vazio" }
        val list = lists.getList(id) ?: return
        lists.upsertList(list.copy(name = trimmed, budgetCents = budgetCents?.takeIf { it > 0 }, updatedAt = time.now()))
    }
}

class SetShoppingListArchivedUseCase @Inject constructor(
    private val lists: ShoppingListRepository,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(id: String, archived: Boolean) {
        val list = lists.getList(id) ?: return
        lists.upsertList(list.copy(archived = archived, updatedAt = time.now()))
    }
}

class DuplicateShoppingListUseCase @Inject constructor(
    private val lists: ShoppingListRepository,
    private val ids: IdProvider,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(id: String, newName: String): ShoppingList =
        lists.duplicate(id, newName.trim(), time.now(), ids::newId)
}

class DeleteShoppingListUseCase @Inject constructor(
    private val lists: ShoppingListRepository,
) {
    /** As sessões já registadas mantêm-se no histórico (FK `SET NULL`). */
    suspend operator fun invoke(id: String) = lists.deleteList(id)
}

data class ListItemInput(
    val name: String,
    val quantity: BigDecimal,
    val unit: MeasureUnit,
    val estimatedPriceCents: Long?,
)

class SaveShoppingListItemUseCase @Inject constructor(
    private val lists: ShoppingListRepository,
    private val ids: IdProvider,
    private val time: TimeProvider,
) {
    /** Cria um item novo quando [itemId] é nulo; caso contrário atualiza-o. */
    suspend operator fun invoke(listId: String, itemId: String?, input: ListItemInput): ShoppingListItem {
        val name = input.name.trim()
        require(name.isNotEmpty()) { "Nome vazio" }
        require(input.quantity.signum() > 0) { "Quantidade inválida" }
        val existing = itemId?.let { lists.getItem(it) }
        val item = ShoppingListItem(
            id = existing?.id ?: ids.newId(),
            shoppingListId = listId,
            productId = existing?.productId,
            name = name,
            plannedQuantity = input.quantity,
            unit = input.unit,
            estimatedPriceCents = input.estimatedPriceCents?.takeIf { it > 0 },
            sortOrder = existing?.sortOrder ?: lists.nextSortOrder(listId),
        )
        lists.upsertItem(item)
        touch(listId)
        return item
    }

    private suspend fun touch(listId: String) {
        lists.getList(listId)?.let { lists.upsertList(it.copy(updatedAt = time.now())) }
    }
}

class DeleteShoppingListItemUseCase @Inject constructor(
    private val lists: ShoppingListRepository,
) {
    suspend operator fun invoke(itemId: String) = lists.deleteItem(itemId)
}

class StartPlannedSessionUseCase @Inject constructor(
    private val lists: ShoppingListRepository,
    private val startSession: StartShoppingSessionUseCase,
) {
    /** O orçamento indicado (por omissão o da lista) é copiado para a sessão (D11). */
    suspend operator fun invoke(listId: String, storeName: String?, budgetCents: Long?): StartSessionResult {
        val list = requireNotNull(lists.getList(listId)) { "Lista inexistente" }
        require(!list.archived) { "Lista arquivada" }
        return startSession(SessionMode.PLANNED, list.id, storeName, budgetCents)
    }
}

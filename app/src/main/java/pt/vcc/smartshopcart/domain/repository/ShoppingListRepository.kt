package pt.vcc.smartshopcart.domain.repository

import kotlinx.coroutines.flow.Flow
import pt.vcc.smartshopcart.domain.model.ShoppingList
import pt.vcc.smartshopcart.domain.model.ShoppingListItem
import pt.vcc.smartshopcart.domain.model.ShoppingListSummary
import pt.vcc.smartshopcart.domain.model.ShoppingListWithItems

interface ShoppingListRepository {
    fun observeLists(archived: Boolean): Flow<List<ShoppingListSummary>>
    fun observeList(id: String): Flow<ShoppingListWithItems?>

    suspend fun getList(id: String): ShoppingList?
    suspend fun getItem(id: String): ShoppingListItem?
    suspend fun upsertList(list: ShoppingList)
    suspend fun deleteList(id: String)

    suspend fun nextSortOrder(listId: String): Int
    suspend fun upsertItem(item: ShoppingListItem)
    suspend fun deleteItem(id: String)

    /** Copia a lista e os itens (com novos IDs) numa única transação. */
    suspend fun duplicate(id: String, newName: String, now: Long, newId: () -> String): ShoppingList
}

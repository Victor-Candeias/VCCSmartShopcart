package pt.vcc.smartshopcart.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import pt.vcc.smartshopcart.core.database.ShoppingDatabase
import pt.vcc.smartshopcart.data.mapper.toDomain
import pt.vcc.smartshopcart.data.mapper.toEntity
import pt.vcc.smartshopcart.domain.model.ShoppingList
import pt.vcc.smartshopcart.domain.model.ShoppingListItem
import pt.vcc.smartshopcart.domain.model.ShoppingListSummary
import pt.vcc.smartshopcart.domain.model.ShoppingListWithItems
import pt.vcc.smartshopcart.domain.repository.ShoppingListRepository
import javax.inject.Inject

class RoomShoppingListRepository @Inject constructor(
    private val db: ShoppingDatabase,
) : ShoppingListRepository {

    private val dao = db.shoppingListDao()

    override fun observeLists(archived: Boolean): Flow<List<ShoppingListSummary>> =
        dao.observeLists(archived).map { rows -> rows.map { ShoppingListSummary(it.list.toDomain(), it.itemCount) } }

    override fun observeList(id: String): Flow<ShoppingListWithItems?> =
        combine(dao.observeList(id), dao.observeItems(id)) { list, items ->
            list?.let { ShoppingListWithItems(it.toDomain(), items.map { item -> item.toDomain() }) }
        }

    override suspend fun getList(id: String): ShoppingList? = dao.getList(id)?.toDomain()

    override suspend fun getItem(id: String): ShoppingListItem? = dao.getItem(id)?.toDomain()

    override suspend fun upsertList(list: ShoppingList) = dao.upsertList(list.toEntity())

    override suspend fun deleteList(id: String) = dao.deleteList(id)

    override suspend fun nextSortOrder(listId: String): Int = dao.nextSortOrder(listId)

    override suspend fun upsertItem(item: ShoppingListItem) = dao.upsertItem(item.toEntity())

    override suspend fun deleteItem(id: String) = dao.deleteItem(id)

    override suspend fun duplicate(id: String, newName: String, now: Long, newId: () -> String): ShoppingList =
        db.withTransaction {
            val source = requireNotNull(dao.getList(id)) { "Lista inexistente" }
            val copy = source.copy(id = newId(), name = newName, createdAt = now, updatedAt = now, archived = false)
            dao.upsertList(copy)
            dao.insertItems(dao.getItems(id).map { it.copy(id = newId(), shoppingListId = copy.id) })
            copy.toDomain()
        }
}

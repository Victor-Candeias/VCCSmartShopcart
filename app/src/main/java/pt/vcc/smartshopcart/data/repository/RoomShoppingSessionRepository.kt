package pt.vcc.smartshopcart.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import pt.vcc.smartshopcart.core.database.ShoppingDatabase
import pt.vcc.smartshopcart.core.database.entity.ProductPriceHistoryEntity
import pt.vcc.smartshopcart.core.database.entity.SessionPlannedItemStateEntity
import pt.vcc.smartshopcart.core.money.LineTotalCalculator
import pt.vcc.smartshopcart.data.mapper.toDomain
import pt.vcc.smartshopcart.data.mapper.toEntity
import pt.vcc.smartshopcart.domain.model.PlannedItemStatus
import pt.vcc.smartshopcart.domain.model.SessionStatus
import pt.vcc.smartshopcart.domain.model.ShoppingSession
import pt.vcc.smartshopcart.domain.model.ShoppingSessionItem
import pt.vcc.smartshopcart.domain.model.ShoppingSessionWithItems
import pt.vcc.smartshopcart.domain.repository.SessionSummary
import pt.vcc.smartshopcart.domain.repository.ShoppingSessionRepository
import java.math.BigDecimal
import javax.inject.Inject

class RoomShoppingSessionRepository @Inject constructor(
    private val db: ShoppingDatabase,
) : ShoppingSessionRepository {

    private val dao = db.shoppingSessionDao()

    override fun observeActiveSession(): Flow<ShoppingSession?> =
        dao.observeLatestWithStatus(SessionStatus.ACTIVE).map { it?.toDomain() }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSession(id: String): Flow<ShoppingSessionWithItems?> =
        dao.observeSession(id).flatMapLatest { session ->
            if (session == null) {
                flowOf(null)
            } else {
                val storeFlow = session.storeId?.let { storeId ->
                    db.storeDao().observeAll().map { stores -> stores.firstOrNull { it.id == storeId }?.toDomain() }
                } ?: flowOf(null)
                val listNameFlow = session.shoppingListId?.let { listId ->
                    db.shoppingListDao().observeList(listId).map { it?.name }
                } ?: flowOf(null)
                combine(dao.observeItems(id), storeFlow, listNameFlow) { items, store, listName ->
                    ShoppingSessionWithItems(session.toDomain(), store, listName, items.map { it.toDomain() })
                }
            }
        }

    override fun observeCompletedSessions(): Flow<List<SessionSummary>> =
        dao.observeSummaries(SessionStatus.COMPLETED).map { rows ->
            rows.map { SessionSummary(it.session.toDomain(), it.storeName, it.listName, it.itemCount) }
        }

    override fun observeSkippedListItems(sessionId: String): Flow<Set<String>> =
        dao.observePlannedStates(sessionId).map { states ->
            states.filter { it.status == PlannedItemStatus.SKIPPED }.mapTo(mutableSetOf()) { it.listItemId }
        }

    override suspend fun getActiveSession(): ShoppingSession? = dao.getLatestWithStatus(SessionStatus.ACTIVE)?.toDomain()

    override suspend fun getSession(id: String): ShoppingSession? = dao.getSession(id)?.toDomain()

    override suspend fun getItem(id: String): ShoppingSessionItem? = dao.getItem(id)?.toDomain()

    override suspend fun createSession(session: ShoppingSession) = dao.insertSession(session.toEntity())

    override suspend fun updateSession(session: ShoppingSession) = dao.upsertSession(session.toEntity())

    override suspend fun addItem(item: ShoppingSessionItem) = dao.insertItem(item.toEntity())

    override suspend fun updateItem(item: ShoppingSessionItem) = dao.upsertItem(item.toEntity())

    override suspend fun removeItem(id: String) = dao.deleteItem(id)

    override suspend fun setPlannedItemStatus(sessionId: String, listItemId: String, status: PlannedItemStatus) {
        if (status == PlannedItemStatus.SKIPPED) {
            dao.upsertPlannedState(SessionPlannedItemStateEntity(sessionId, listItemId, status))
        } else {
            dao.deletePlannedState(sessionId, listItemId)
        }
    }

    override suspend fun completeSession(id: String, completedAt: Long, newHistoryId: () -> String): ShoppingSession =
        db.withTransaction {
            val session = requireNotNull(dao.getSession(id)) { "Sessão inexistente" }
            check(session.status == SessionStatus.ACTIVE) { "Sessão já terminada" }
            val items = dao.getItems(id)
            val total = LineTotalCalculator.sum(items.map { it.totalCents })
            val completed = session.copy(
                status = SessionStatus.COMPLETED,
                completedAt = completedAt,
                finalTotalCents = total,
            )
            dao.upsertSession(completed)
            dao.snapshotProducts(id)
            val history = items
                .filter { it.barcode != null || it.productId != null }
                .map { item ->
                    ProductPriceHistoryEntity(
                        id = newHistoryId(),
                        productId = item.productId,
                        barcode = item.barcode,
                        storeId = session.storeId,
                        priceCents = item.unitPriceCents,
                        unit = item.unit.priceUnit,
                        quantity = item.unit.toPriceUnitQuantity(BigDecimal(item.quantity)).toPlainString(),
                        source = item.priceSource,
                        observedAt = completedAt,
                        sessionItemId = item.id,
                    )
                }
            db.priceHistoryDao().insertAll(history)
            completed.toDomain()
        }

    override suspend fun deleteSession(id: String) = dao.deleteSession(id)
}

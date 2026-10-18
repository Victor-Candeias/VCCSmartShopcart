package pt.vcc.smartshopcart.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import pt.vcc.smartshopcart.core.database.entity.PriceCaptureEntity
import pt.vcc.smartshopcart.core.database.entity.ProductEntity
import pt.vcc.smartshopcart.core.database.entity.ProductPriceHistoryEntity
import pt.vcc.smartshopcart.core.database.entity.SessionPlannedItemStateEntity
import pt.vcc.smartshopcart.core.database.entity.ShoppingListEntity
import pt.vcc.smartshopcart.core.database.entity.ShoppingListItemEntity
import pt.vcc.smartshopcart.core.database.entity.ShoppingSessionEntity
import pt.vcc.smartshopcart.core.database.entity.ShoppingSessionItemEntity
import pt.vcc.smartshopcart.core.database.entity.StoreEntity
import pt.vcc.smartshopcart.domain.model.SessionStatus

data class ShoppingListWithCountRow(
    @Embedded val list: ShoppingListEntity,
    val itemCount: Int,
)

data class SessionSummaryRow(
    @Embedded val session: ShoppingSessionEntity,
    val storeName: String?,
    val listName: String?,
    val itemCount: Int,
)

data class PriceHistoryRow(
    @Embedded val price: ProductPriceHistoryEntity,
    val storeName: String?,
)

@Dao
interface ShoppingListDao {
    @Query(
        """
        SELECT l.*, (SELECT COUNT(*) FROM shopping_list_items i WHERE i.shoppingListId = l.id) AS itemCount
        FROM shopping_lists l WHERE l.archived = :archived ORDER BY l.updatedAt DESC
        """,
    )
    fun observeLists(archived: Boolean): Flow<List<ShoppingListWithCountRow>>

    @Query("SELECT * FROM shopping_lists WHERE id = :id")
    fun observeList(id: String): Flow<ShoppingListEntity?>

    @Query("SELECT * FROM shopping_lists WHERE id = :id")
    suspend fun getList(id: String): ShoppingListEntity?

    @Query("SELECT * FROM shopping_list_items WHERE shoppingListId = :listId ORDER BY sortOrder, name")
    fun observeItems(listId: String): Flow<List<ShoppingListItemEntity>>

    @Query("SELECT * FROM shopping_list_items WHERE shoppingListId = :listId ORDER BY sortOrder, name")
    suspend fun getItems(listId: String): List<ShoppingListItemEntity>

    @Query("SELECT * FROM shopping_list_items WHERE id = :id")
    suspend fun getItem(id: String): ShoppingListItemEntity?

    @Upsert
    suspend fun upsertList(list: ShoppingListEntity)

    @Upsert
    suspend fun upsertItem(item: ShoppingListItemEntity)

    @Insert
    suspend fun insertItems(items: List<ShoppingListItemEntity>)

    @Query("DELETE FROM shopping_list_items WHERE id = :id")
    suspend fun deleteItem(id: String)

    @Query("DELETE FROM shopping_lists WHERE id = :id")
    suspend fun deleteList(id: String)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM shopping_list_items WHERE shoppingListId = :listId")
    suspend fun nextSortOrder(listId: String): Int
}

@Dao
interface ShoppingSessionDao {
    @Insert
    suspend fun insertSession(session: ShoppingSessionEntity)

    @Upsert
    suspend fun upsertSession(session: ShoppingSessionEntity)

    @Query("SELECT * FROM shopping_sessions WHERE id = :id")
    fun observeSession(id: String): Flow<ShoppingSessionEntity?>

    @Query("SELECT * FROM shopping_sessions WHERE id = :id")
    suspend fun getSession(id: String): ShoppingSessionEntity?

    @Query("SELECT * FROM shopping_sessions WHERE status = :status ORDER BY startedAt DESC LIMIT 1")
    fun observeLatestWithStatus(status: SessionStatus = SessionStatus.ACTIVE): Flow<ShoppingSessionEntity?>

    @Query("SELECT * FROM shopping_sessions WHERE status = :status ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatestWithStatus(status: SessionStatus = SessionStatus.ACTIVE): ShoppingSessionEntity?

    @Query(
        """
        SELECT s.*, st.name AS storeName, l.name AS listName,
            (SELECT COUNT(*) FROM shopping_session_items i WHERE i.sessionId = s.id) AS itemCount
        FROM shopping_sessions s
        LEFT JOIN stores st ON st.id = s.storeId
        LEFT JOIN shopping_lists l ON l.id = s.shoppingListId
        WHERE s.status = :status ORDER BY COALESCE(s.completedAt, s.startedAt) DESC
        """,
    )
    fun observeSummaries(status: SessionStatus = SessionStatus.COMPLETED): Flow<List<SessionSummaryRow>>

    @Query("DELETE FROM shopping_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("SELECT * FROM shopping_session_items WHERE sessionId = :sessionId ORDER BY addedAt DESC")
    fun observeItems(sessionId: String): Flow<List<ShoppingSessionItemEntity>>

    @Query("SELECT * FROM shopping_session_items WHERE sessionId = :sessionId ORDER BY addedAt DESC")
    suspend fun getItems(sessionId: String): List<ShoppingSessionItemEntity>

    @Query("SELECT * FROM shopping_session_items WHERE id = :id")
    suspend fun getItem(id: String): ShoppingSessionItemEntity?

    @Insert
    suspend fun insertItem(item: ShoppingSessionItemEntity)

    @Upsert
    suspend fun upsertItem(item: ShoppingSessionItemEntity)

    @Query("DELETE FROM shopping_session_items WHERE id = :id")
    suspend fun deleteItem(id: String)

    @Query("SELECT COALESCE(SUM(totalCents), 0) FROM shopping_session_items WHERE sessionId = :sessionId")
    suspend fun sumTotal(sessionId: String): Long

    @Query("SELECT * FROM session_planned_item_states WHERE sessionId = :sessionId")
    fun observePlannedStates(sessionId: String): Flow<List<SessionPlannedItemStateEntity>>

    @Upsert
    suspend fun upsertPlannedState(state: SessionPlannedItemStateEntity)

    @Query("DELETE FROM session_planned_item_states WHERE sessionId = :sessionId AND listItemId = :listItemId")
    suspend fun deletePlannedState(sessionId: String, listItemId: String)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE barcode = :barcode ORDER BY updatedAt DESC LIMIT 1")
    suspend fun findByBarcode(barcode: String): ProductEntity?

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: String): ProductEntity?

    @Upsert
    suspend fun upsert(product: ProductEntity)
}

@Dao
interface StoreDao {
    @Query("SELECT * FROM stores ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<StoreEntity>>

    @Query("SELECT * FROM stores WHERE id = :id")
    suspend fun getById(id: String): StoreEntity?

    @Query("SELECT * FROM stores WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): StoreEntity?

    @Insert
    suspend fun insert(store: StoreEntity)
}

@Dao
interface PriceHistoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<ProductPriceHistoryEntity>)

    @Query(
        """
        SELECT * FROM product_price_history
        WHERE barcode = :barcode AND ((:storeId IS NULL AND storeId IS NULL) OR storeId = :storeId)
        ORDER BY observedAt DESC LIMIT 1
        """,
    )
    suspend fun lastForBarcode(barcode: String, storeId: String?): ProductPriceHistoryEntity?

    @Query(
        """
        SELECT h.*, s.name AS storeName FROM product_price_history h
        LEFT JOIN stores s ON s.id = h.storeId
        WHERE h.barcode = :barcode ORDER BY h.observedAt DESC
        """,
    )
    fun observeForBarcode(barcode: String): Flow<List<PriceHistoryRow>>
}

@Dao
interface PriceCaptureDao {
    @Insert
    suspend fun insert(capture: PriceCaptureEntity)

    @Query("SELECT * FROM price_captures WHERE sessionItemId = :sessionItemId ORDER BY capturedAt DESC")
    suspend fun getForItem(sessionItemId: String): List<PriceCaptureEntity>
}

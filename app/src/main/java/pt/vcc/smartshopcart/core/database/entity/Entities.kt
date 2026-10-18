package pt.vcc.smartshopcart.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import pt.vcc.smartshopcart.domain.model.CaptureStatus
import pt.vcc.smartshopcart.domain.model.MeasureUnit
import pt.vcc.smartshopcart.domain.model.PlannedItemStatus
import pt.vcc.smartshopcart.domain.model.PriceSource
import pt.vcc.smartshopcart.domain.model.ProductSource
import pt.vcc.smartshopcart.domain.model.SessionMode
import pt.vcc.smartshopcart.domain.model.SessionStatus

@Entity(tableName = "shopping_lists")
data class ShoppingListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val budgetCents: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val archived: Boolean,
)

@Entity(
    tableName = "shopping_list_items",
    foreignKeys = [
        ForeignKey(
            entity = ShoppingListEntity::class,
            parentColumns = ["id"],
            childColumns = ["shoppingListId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("shoppingListId"), Index("productId")],
)
data class ShoppingListItemEntity(
    @PrimaryKey val id: String,
    val shoppingListId: String,
    val productId: String?,
    val name: String,
    /** Decimal como texto para não perder precisão (`BigDecimal` no domínio). */
    val plannedQuantity: String,
    val unit: MeasureUnit,
    val estimatedPriceCents: Long?,
    val sortOrder: Int,
)

@Entity(tableName = "stores", indices = [Index("name")])
data class StoreEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "shopping_sessions",
    foreignKeys = [
        ForeignKey(
            entity = ShoppingListEntity::class,
            parentColumns = ["id"],
            childColumns = ["shoppingListId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = StoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["storeId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("status", "startedAt"),
        Index("status", "completedAt"),
        Index("shoppingListId"),
        Index("storeId"),
    ],
)
data class ShoppingSessionEntity(
    @PrimaryKey val id: String,
    val shoppingListId: String?,
    val mode: SessionMode,
    val status: SessionStatus,
    val storeId: String?,
    val budgetCents: Long?,
    val startedAt: Long,
    val completedAt: Long?,
    val finalTotalCents: Long?,
)

@Entity(
    tableName = "shopping_session_items",
    foreignKeys = [
        ForeignKey(
            entity = ShoppingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ShoppingListItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["shoppingListItemId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("sessionId"), Index("shoppingListItemId"), Index("productId")],
)
data class ShoppingSessionItemEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val shoppingListItemId: String?,
    val productId: String?,
    val name: String?,
    val barcode: String?,
    val quantity: String,
    val unit: MeasureUnit,
    val unitPriceCents: Long,
    val discountCents: Long,
    val totalCents: Long,
    val priceSource: PriceSource,
    val addedAt: Long,
    /** Nome e categoria do produto fixados ao concluir a sessão (relatórios históricos). */
    val productNameSnapshot: String? = null,
    val categorySnapshot: String? = null,
)

/** `barcode` não é chave nem único (D7): o mesmo código pode ter vários registos. */
@Entity(tableName = "products", indices = [Index("barcode")])
data class ProductEntity(
    @PrimaryKey val id: String,
    val barcode: String?,
    val name: String,
    val brand: String?,
    val quantityLabel: String?,
    val category: String?,
    val imageUrl: String?,
    val source: ProductSource,
    val updatedAt: Long,
)

@Entity(
    tableName = "product_price_history",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = StoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["storeId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ShoppingSessionItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionItemId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("productId", "storeId", "observedAt"),
        Index("barcode", "storeId", "observedAt"),
        Index("storeId"),
        // D13: um registo por item de sessão, para não duplicar o histórico.
        Index(value = ["sessionItemId"], unique = true),
    ],
)
data class ProductPriceHistoryEntity(
    @PrimaryKey val id: String,
    val productId: String?,
    val barcode: String?,
    val storeId: String?,
    val priceCents: Long,
    val unit: MeasureUnit,
    val quantity: String,
    val source: PriceSource,
    val observedAt: Long,
    val sessionItemId: String?,
)

@Entity(
    tableName = "price_captures",
    foreignKeys = [
        ForeignKey(
            entity = ShoppingSessionItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionItemId")],
)
data class PriceCaptureEntity(
    @PrimaryKey val id: String,
    val sessionItemId: String?,
    val rawText: String,
    /** Lista de `PriceCandidate` em JSON (D8). */
    val candidatesJson: String,
    val detectedPriceCents: Long?,
    val confirmedPriceCents: Long?,
    val status: CaptureStatus,
    val capturedAt: Long,
)

/** Apenas `SKIPPED` é persistido; `ADDED` e `PENDING` derivam dos itens da sessão (D12). */
@Entity(
    tableName = "session_planned_item_states",
    primaryKeys = ["sessionId", "listItemId"],
    foreignKeys = [
        ForeignKey(
            entity = ShoppingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ShoppingListItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["listItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("listItemId")],
)
data class SessionPlannedItemStateEntity(
    val sessionId: String,
    val listItemId: String,
    val status: PlannedItemStatus,
)

package pt.vcc.smartshopcart.domain.model

import java.math.BigDecimal

data class ShoppingList(
    val id: String,
    val name: String,
    val budgetCents: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val archived: Boolean,
)

data class ShoppingListItem(
    val id: String,
    val shoppingListId: String,
    val productId: String?,
    val name: String,
    val plannedQuantity: BigDecimal,
    val unit: MeasureUnit,
    val estimatedPriceCents: Long?,
    val sortOrder: Int,
)

data class ShoppingListSummary(
    val list: ShoppingList,
    val itemCount: Int,
)

data class ShoppingListWithItems(
    val list: ShoppingList,
    val items: List<ShoppingListItem>,
)

data class Store(
    val id: String,
    val name: String,
    val createdAt: Long,
)

data class ShoppingSession(
    val id: String,
    val shoppingListId: String?,
    val mode: SessionMode,
    val status: SessionStatus,
    val storeId: String?,
    val budgetCents: Long?,
    val startedAt: Long,
    val completedAt: Long?,
    val finalTotalCents: Long?,
)

data class ShoppingSessionItem(
    val id: String,
    val sessionId: String,
    val shoppingListItemId: String?,
    val productId: String?,
    val name: String?,
    val barcode: String?,
    val quantity: BigDecimal,
    val unit: MeasureUnit,
    val unitPriceCents: Long,
    val discountCents: Long,
    val totalCents: Long,
    val priceSource: PriceSource,
    val addedAt: Long,
)

data class ShoppingSessionWithItems(
    val session: ShoppingSession,
    val store: Store?,
    val listName: String?,
    val items: List<ShoppingSessionItem>,
)

data class Product(
    val id: String,
    val barcode: String?,
    val name: String,
    val brand: String?,
    val quantityLabel: String?,
    val category: String?,
    val imageUrl: String?,
    val source: ProductSource,
    val updatedAt: Long,
)

data class ProductPrice(
    val priceCents: Long,
    val unit: MeasureUnit,
    val storeName: String?,
    val observedAt: Long,
    val source: PriceSource,
)

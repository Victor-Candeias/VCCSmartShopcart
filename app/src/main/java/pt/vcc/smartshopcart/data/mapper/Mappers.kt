package pt.vcc.smartshopcart.data.mapper

import pt.vcc.smartshopcart.core.database.entity.ProductEntity
import pt.vcc.smartshopcart.core.database.entity.ShoppingListEntity
import pt.vcc.smartshopcart.core.database.entity.ShoppingListItemEntity
import pt.vcc.smartshopcart.core.database.entity.ShoppingSessionEntity
import pt.vcc.smartshopcart.core.database.entity.ShoppingSessionItemEntity
import pt.vcc.smartshopcart.core.database.entity.StoreEntity
import pt.vcc.smartshopcart.domain.model.Product
import pt.vcc.smartshopcart.domain.model.ShoppingList
import pt.vcc.smartshopcart.domain.model.ShoppingListItem
import pt.vcc.smartshopcart.domain.model.ShoppingSession
import pt.vcc.smartshopcart.domain.model.ShoppingSessionItem
import pt.vcc.smartshopcart.domain.model.Store
import java.math.BigDecimal

fun ShoppingSessionEntity.toDomain() = ShoppingSession(
    id, shoppingListId, mode, status, storeId, budgetCents, startedAt, completedAt, finalTotalCents,
)

fun ShoppingSession.toEntity() = ShoppingSessionEntity(
    id, shoppingListId, mode, status, storeId, budgetCents, startedAt, completedAt, finalTotalCents,
)

fun ShoppingSessionItemEntity.toDomain() = ShoppingSessionItem(
    id = id,
    sessionId = sessionId,
    shoppingListItemId = shoppingListItemId,
    productId = productId,
    name = name,
    barcode = barcode,
    quantity = BigDecimal(quantity),
    unit = unit,
    unitPriceCents = unitPriceCents,
    discountCents = discountCents,
    totalCents = totalCents,
    priceSource = priceSource,
    addedAt = addedAt,
)

fun ShoppingSessionItem.toEntity() = ShoppingSessionItemEntity(
    id = id,
    sessionId = sessionId,
    shoppingListItemId = shoppingListItemId,
    productId = productId,
    name = name,
    barcode = barcode,
    quantity = quantity.toPlainString(),
    unit = unit,
    unitPriceCents = unitPriceCents,
    discountCents = discountCents,
    totalCents = totalCents,
    priceSource = priceSource,
    addedAt = addedAt,
)

fun StoreEntity.toDomain() = Store(id, name, createdAt)

fun ShoppingListEntity.toDomain() = ShoppingList(id, name, budgetCents, createdAt, updatedAt, archived)

fun ShoppingList.toEntity() = ShoppingListEntity(id, name, budgetCents, createdAt, updatedAt, archived)

fun ShoppingListItemEntity.toDomain() = ShoppingListItem(
    id, shoppingListId, productId, name, BigDecimal(plannedQuantity), unit, estimatedPriceCents, sortOrder,
)

fun ShoppingListItem.toEntity() = ShoppingListItemEntity(
    id, shoppingListId, productId, name, plannedQuantity.toPlainString(), unit, estimatedPriceCents, sortOrder,
)

fun ProductEntity.toDomain() = Product(id, barcode, name, brand, quantityLabel, category, imageUrl, source, updatedAt)

fun Product.toEntity() = ProductEntity(id, barcode, name, brand, quantityLabel, category, imageUrl, source, updatedAt)

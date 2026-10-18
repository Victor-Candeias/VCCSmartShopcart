package pt.vcc.smartshopcart.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import pt.vcc.smartshopcart.core.common.IdProvider
import pt.vcc.smartshopcart.core.common.TimeProvider
import pt.vcc.smartshopcart.core.database.ShoppingDatabase
import pt.vcc.smartshopcart.core.database.entity.EstablishmentEntity
import pt.vcc.smartshopcart.core.database.entity.ExpenseCategoryEntity
import pt.vcc.smartshopcart.core.database.entity.ExpenseEntity
import pt.vcc.smartshopcart.core.database.entity.ExpenseItemEntity
import pt.vcc.smartshopcart.core.database.entity.ExpenseProductEntity
import pt.vcc.smartshopcart.domain.model.expense.Establishment
import pt.vcc.smartshopcart.domain.model.expense.Expense
import pt.vcc.smartshopcart.domain.model.expense.ExpenseCategory
import pt.vcc.smartshopcart.domain.model.expense.ExpenseDetails
import pt.vcc.smartshopcart.domain.model.expense.ExpenseDraft
import pt.vcc.smartshopcart.domain.model.expense.ExpenseItem
import pt.vcc.smartshopcart.domain.model.expense.ExpenseProduct
import pt.vcc.smartshopcart.domain.model.expense.ExpenseSummary
import pt.vcc.smartshopcart.domain.repository.ExpenseRepository
import javax.inject.Inject

class RoomExpenseRepository @Inject constructor(
    private val db: ShoppingDatabase,
    private val ids: IdProvider,
    private val time: TimeProvider,
) : ExpenseRepository {
    private val dao = db.expenseDao()

    override fun observeCategories(includeInactive: Boolean): Flow<List<ExpenseCategory>> =
        dao.observeCategories(includeInactive).map { list -> list.map { it.toDomain() } }

    override fun observeProducts(categoryId: String?, includeInactive: Boolean): Flow<List<ExpenseProduct>> =
        dao.observeProducts(categoryId, includeInactive).map { list -> list.map { it.toDomain() } }

    override fun observeExpenses(): Flow<List<ExpenseSummary>> =
        combine(dao.observeSummaries(), dao.observeCategoryLinks()) { rows, links ->
            val categoriesByExpense = links.groupBy({ it.expenseId }, { it.categoryId })
            rows.map { row ->
                ExpenseSummary(
                    expense = row.expense.toDomain(),
                    establishmentName = row.establishmentName,
                    itemCount = row.itemCount,
                    categoryIds = categoriesByExpense[row.expense.id].orEmpty().toSet(),
                )
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeExpense(id: String): Flow<ExpenseDetails?> =
        dao.observeExpense(id).flatMapLatest { expense ->
            if (expense == null) {
                flowOf(null)
            } else {
                dao.observeItems(id).map { items ->
                    ExpenseDetails(
                        expense = expense.toDomain(),
                        establishment = expense.establishmentId?.let { dao.getEstablishment(it) }?.toDomain(),
                        items = items.map { it.toDomain() },
                    )
                }
            }
        }

    override fun observeEstablishments(): Flow<List<Establishment>> =
        dao.observeEstablishments().map { list -> list.map { it.toDomain() } }

    override suspend fun saveExpense(draft: ExpenseDraft, expenseId: String?): String = db.withTransaction {
        require(draft.items.isNotEmpty()) { "Despesa sem itens" }
        draft.items.forEach { item ->
            require(item.name.isNotBlank()) { "Item sem nome" }
            require(item.quantity >= 1) { "Quantidade inválida" }
            require(item.unitPriceCents >= 0) { "Preço negativo" }
        }
        val now = time.now()
        val existing = expenseId?.let { dao.getExpense(it) }
        require(expenseId == null || existing != null) { "Despesa inexistente" }
        val id = existing?.id ?: ids.newId()
        val establishmentId = draft.establishmentName?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
            dao.findEstablishmentByName(name)?.id
                ?: EstablishmentEntity(ids.newId(), name, type = null, isFavorite = false, createdAt = now)
                    .also { dao.insertEstablishment(it) }.id
        }
        dao.upsertExpense(
            ExpenseEntity(
                id = id,
                establishmentId = establishmentId,
                occurredAt = draft.occurredAt,
                totalCents = 0,
                notes = draft.notes?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        dao.deleteItems(id)
        dao.insertItems(
            draft.items.mapIndexed { index, item ->
                ExpenseItemEntity(
                    id = ids.newId(),
                    expenseId = id,
                    productId = item.productId,
                    nameSnapshot = item.name.trim(),
                    quantity = item.quantity,
                    unitPriceCents = item.unitPriceCents,
                    totalCents = item.totalCents,
                    position = index,
                )
            },
        )
        dao.setTotal(id, dao.sumItems(id))
        id
    }

    override suspend fun getExpense(id: String): ExpenseDetails? {
        val expense = dao.getExpense(id) ?: return null
        return ExpenseDetails(
            expense = expense.toDomain(),
            establishment = expense.establishmentId?.let { dao.getEstablishment(it) }?.toDomain(),
            items = dao.getItems(id).map { it.toDomain() },
        )
    }

    override suspend fun deleteExpense(id: String) = dao.deleteExpense(id)

    override suspend fun lastPaidPrice(productId: String, establishmentName: String?): Long? =
        if (establishmentName == null) dao.lastPrice(productId) else dao.lastPriceAt(productId, establishmentName)

    override suspend fun getProduct(id: String): ExpenseProduct? = dao.getProduct(id)?.toDomain()

    override suspend fun upsertProduct(product: ExpenseProduct) = dao.upsertProduct(product.toEntity())

    override suspend fun nextProductSortOrder(categoryId: String): Int = dao.nextProductSortOrder(categoryId)

    override suspend fun reorderProducts(orderedIds: List<String>) {
        db.withTransaction {
            orderedIds.forEachIndexed { index, id -> dao.setProductSortOrder(id, index) }
        }
    }

    override suspend fun importCatalog(categories: List<ExpenseCategory>, products: List<ExpenseProduct>) {
        db.withTransaction {
            dao.insertCategoriesIgnoringExisting(categories.map { it.toEntity() })
            dao.insertProductsIgnoringExisting(products.map { it.toEntity() })
        }
    }
}

private fun ExpenseCategoryEntity.toDomain() = ExpenseCategory(id, name, icon, sortOrder, isActive)

private fun ExpenseCategory.toEntity() = ExpenseCategoryEntity(id, name, icon, sortOrder, isActive)

private fun ExpenseProductEntity.toDomain() =
    ExpenseProduct(id, categoryId, name, icon, defaultPriceCents, isFavorite, isActive, sortOrder)

private fun ExpenseProduct.toEntity() =
    ExpenseProductEntity(id, categoryId, name, icon, defaultPriceCents, isFavorite, isActive, sortOrder)

private fun EstablishmentEntity.toDomain() = Establishment(id, name, type, isFavorite)

private fun ExpenseEntity.toDomain() = Expense(id, establishmentId, occurredAt, totalCents, notes, createdAt, updatedAt)

private fun ExpenseItemEntity.toDomain() =
    ExpenseItem(id, expenseId, productId, nameSnapshot, quantity, unitPriceCents, totalCents)

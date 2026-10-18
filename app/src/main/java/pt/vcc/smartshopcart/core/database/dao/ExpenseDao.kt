package pt.vcc.smartshopcart.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import pt.vcc.smartshopcart.core.database.entity.EstablishmentEntity
import pt.vcc.smartshopcart.core.database.entity.ExpenseCategoryEntity
import pt.vcc.smartshopcart.core.database.entity.ExpenseEntity
import pt.vcc.smartshopcart.core.database.entity.ExpenseItemEntity
import pt.vcc.smartshopcart.core.database.entity.ExpenseProductEntity

data class ExpenseSummaryRow(
    @Embedded val expense: ExpenseEntity,
    val establishmentName: String?,
    val itemCount: Int,
)

data class ExpenseCategoryLinkRow(
    val expenseId: String,
    val categoryId: String,
)

@Dao
interface ExpenseDao {
    // Catálogo

    @Query("SELECT * FROM expense_categories WHERE isActive = 1 OR :includeInactive ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeCategories(includeInactive: Boolean): Flow<List<ExpenseCategoryEntity>>

    @Query(
        """
        SELECT * FROM expense_products
        WHERE (:categoryId IS NULL OR categoryId = :categoryId) AND (isActive = 1 OR :includeInactive)
        ORDER BY sortOrder, name COLLATE NOCASE
        """,
    )
    fun observeProducts(categoryId: String?, includeInactive: Boolean): Flow<List<ExpenseProductEntity>>

    @Query("SELECT * FROM expense_products WHERE id = :id")
    suspend fun getProduct(id: String): ExpenseProductEntity?

    @Query("SELECT * FROM expense_categories WHERE id = :id")
    suspend fun getCategory(id: String): ExpenseCategoryEntity?

    @Upsert
    suspend fun upsertProduct(product: ExpenseProductEntity)

    @Query("UPDATE expense_products SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setProductSortOrder(id: String, sortOrder: Int)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM expense_products WHERE categoryId = :categoryId")
    suspend fun nextProductSortOrder(categoryId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategoriesIgnoringExisting(categories: List<ExpenseCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProductsIgnoringExisting(products: List<ExpenseProductEntity>)

    // Estabelecimentos

    @Query("SELECT * FROM establishments ORDER BY isFavorite DESC, name COLLATE NOCASE")
    fun observeEstablishments(): Flow<List<EstablishmentEntity>>

    @Query("SELECT * FROM establishments WHERE id = :id")
    suspend fun getEstablishment(id: String): EstablishmentEntity?

    @Query("SELECT * FROM establishments WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findEstablishmentByName(name: String): EstablishmentEntity?

    @Insert
    suspend fun insertEstablishment(establishment: EstablishmentEntity)

    // Despesas

    @Query(
        """
        SELECT e.*, est.name AS establishmentName,
            (SELECT COUNT(*) FROM expense_items i WHERE i.expenseId = e.id) AS itemCount
        FROM expenses e LEFT JOIN establishments est ON est.id = e.establishmentId
        ORDER BY e.occurredAt DESC, e.createdAt DESC
        """,
    )
    fun observeSummaries(): Flow<List<ExpenseSummaryRow>>

    @Query(
        """
        SELECT DISTINCT i.expenseId AS expenseId, p.categoryId AS categoryId
        FROM expense_items i JOIN expense_products p ON p.id = i.productId
        """,
    )
    fun observeCategoryLinks(): Flow<List<ExpenseCategoryLinkRow>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    fun observeExpense(id: String): Flow<ExpenseEntity?>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpense(id: String): ExpenseEntity?

    @Upsert
    suspend fun upsertExpense(expense: ExpenseEntity)

    @Query("UPDATE expenses SET totalCents = :totalCents WHERE id = :id")
    suspend fun setTotal(id: String, totalCents: Long)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpense(id: String)

    @Query("SELECT * FROM expense_items WHERE expenseId = :expenseId ORDER BY position")
    fun observeItems(expenseId: String): Flow<List<ExpenseItemEntity>>

    @Query("SELECT * FROM expense_items WHERE expenseId = :expenseId ORDER BY position")
    suspend fun getItems(expenseId: String): List<ExpenseItemEntity>

    @Insert
    suspend fun insertItems(items: List<ExpenseItemEntity>)

    @Query("DELETE FROM expense_items WHERE expenseId = :expenseId")
    suspend fun deleteItems(expenseId: String)

    @Query("SELECT COALESCE(SUM(totalCents), 0) FROM expense_items WHERE expenseId = :expenseId")
    suspend fun sumItems(expenseId: String): Long

    /** Último preço unitário pago pelo produto no estabelecimento indicado (nome sem distinção de maiúsculas). */
    @Query(
        """
        SELECT i.unitPriceCents FROM expense_items i
        JOIN expenses e ON e.id = i.expenseId
        JOIN establishments est ON est.id = e.establishmentId
        WHERE i.productId = :productId AND est.name = :establishmentName COLLATE NOCASE
        ORDER BY e.occurredAt DESC, e.updatedAt DESC, i.position DESC
        LIMIT 1
        """,
    )
    suspend fun lastPriceAt(productId: String, establishmentName: String): Long?

    /** Último preço unitário pago pelo produto em qualquer estabelecimento. */
    @Query(
        """
        SELECT i.unitPriceCents FROM expense_items i
        JOIN expenses e ON e.id = i.expenseId
        WHERE i.productId = :productId
        ORDER BY e.occurredAt DESC, e.updatedAt DESC, i.position DESC
        LIMIT 1
        """,
    )
    suspend fun lastPrice(productId: String): Long?
}

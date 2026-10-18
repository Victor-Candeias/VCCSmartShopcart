package pt.vcc.smartshopcart.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Despesas Rápidas: tabelas próprias, sem ligação a `products` (EAN/GTIN) nem a `stores`.

@Entity(tableName = "expense_categories")
data class ExpenseCategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String?,
    val sortOrder: Int,
    val isActive: Boolean,
)

@Entity(
    tableName = "expense_products",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
        ),
    ],
    indices = [Index("categoryId", "sortOrder")],
)
data class ExpenseProductEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val name: String,
    val icon: String?,
    val defaultPriceCents: Long?,
    val isFavorite: Boolean,
    val isActive: Boolean,
    val sortOrder: Int,
)

@Entity(tableName = "establishments", indices = [Index("name")])
data class EstablishmentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String?,
    val isFavorite: Boolean,
    val createdAt: Long,
)

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = EstablishmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["establishmentId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("occurredAt"), Index("establishmentId")],
)
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val establishmentId: String?,
    /** Instante em epoch millis UTC. */
    val occurredAt: Long,
    val totalCents: Long,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "expense_items",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExpenseProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("expenseId"), Index("productId")],
)
data class ExpenseItemEntity(
    @PrimaryKey val id: String,
    val expenseId: String,
    val productId: String?,
    val nameSnapshot: String,
    val quantity: Int,
    val unitPriceCents: Long,
    val totalCents: Long,
    /** Ordem de apresentação dentro da despesa. */
    val position: Int,
    /** Categoria do produto no momento da gravação, para os relatórios não mudarem com o catálogo. */
    val categoryIdSnapshot: String? = null,
    val categoryNameSnapshot: String? = null,
)

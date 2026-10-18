package pt.vcc.smartshopcart.core.database.dao

import androidx.room.Dao
import androidx.room.Query

/**
 * Linha normalizada de relatório (um item de uma transação real). Os IDs das dimensões levam o
 * prefixo da tabela de origem para não colidirem entre modos.
 */
data class ReportRowDb(
    val transactionId: String,
    val itemId: String,
    val occurredAtMillis: Long,
    val mode: String,
    val establishmentId: String?,
    val establishmentName: String?,
    val categoryId: String?,
    val categoryName: String?,
    val productId: String?,
    val productName: String?,
    val quantityText: String,
    val unit: String,
    val unitPriceCents: Long,
    val discountCents: Long,
    val totalCents: Long,
)

data class ReportBudgetDb(
    val transactionId: String,
    val mode: String,
    val occurredAtMillis: Long,
    val establishmentId: String?,
    val establishmentName: String?,
    val listName: String?,
    val budgetCents: Long,
    val spentCents: Long,
)

/**
 * Leitura para relatórios: apenas sessões `COMPLETED` e despesas gravadas, no intervalo
 * `[fromMillis, untilMillis)`. Listas planeadas sem compra concluída nunca entram.
 */
@Dao
interface ReportsDao {
    @Query(
        """
        SELECT
            e.id AS transactionId,
            i.id AS itemId,
            e.occurredAt AS occurredAtMillis,
            'EXPENSE' AS mode,
            'establishment:' || e.establishmentId AS establishmentId,
            est.name AS establishmentName,
            'expense-category:' || COALESCE(i.categoryIdSnapshot, p.categoryId) AS categoryId,
            COALESCE(i.categoryNameSnapshot, c.name) AS categoryName,
            'expense-product:' || i.productId AS productId,
            i.nameSnapshot AS productName,
            CAST(i.quantity AS TEXT) AS quantityText,
            'UN' AS unit,
            i.unitPriceCents AS unitPriceCents,
            0 AS discountCents,
            i.totalCents AS totalCents
        FROM expenses e
        JOIN expense_items i ON i.expenseId = e.id
        LEFT JOIN establishments est ON est.id = e.establishmentId
        LEFT JOIN expense_products p ON p.id = i.productId
        LEFT JOIN expense_categories c ON c.id = p.categoryId
        WHERE e.occurredAt >= :fromMillis AND e.occurredAt < :untilMillis
        ORDER BY e.occurredAt DESC, e.id, i.position
        """,
    )
    suspend fun getExpenseRows(fromMillis: Long, untilMillis: Long): List<ReportRowDb>

    /** [sessionModes]: valores de `SessionMode` (`CALCULATOR`, `PLANNED`). */
    @Query(
        """
        SELECT
            s.id AS transactionId,
            i.id AS itemId,
            s.completedAt AS occurredAtMillis,
            CASE s.mode WHEN 'PLANNED' THEN 'PLANNED_LIST' ELSE 'CALCULATOR' END AS mode,
            'store:' || s.storeId AS establishmentId,
            st.name AS establishmentName,
            'product-category:' || NULLIF(LOWER(TRIM(COALESCE(i.categorySnapshot, p.category))), '') AS categoryId,
            NULLIF(TRIM(COALESCE(i.categorySnapshot, p.category)), '') AS categoryName,
            'product:' || i.productId AS productId,
            COALESCE(i.name, i.productNameSnapshot, p.name) AS productName,
            i.quantity AS quantityText,
            i.unit AS unit,
            i.unitPriceCents AS unitPriceCents,
            i.discountCents AS discountCents,
            i.totalCents AS totalCents
        FROM shopping_sessions s
        JOIN shopping_session_items i ON i.sessionId = s.id
        LEFT JOIN stores st ON st.id = s.storeId
        LEFT JOIN products p ON p.id = i.productId
        WHERE s.status = 'COMPLETED' AND s.mode IN (:sessionModes)
            AND s.completedAt >= :fromMillis AND s.completedAt < :untilMillis
        ORDER BY s.completedAt DESC, s.id, i.addedAt, i.id
        """,
    )
    suspend fun getCompletedSessionRows(fromMillis: Long, untilMillis: Long, sessionModes: List<String>): List<ReportRowDb>

    /** Orçamento vs. realizado: o realizado é o total de todos os itens da compra concluída. */
    @Query(
        """
        SELECT
            s.id AS transactionId,
            CASE s.mode WHEN 'PLANNED' THEN 'PLANNED_LIST' ELSE 'CALCULATOR' END AS mode,
            s.completedAt AS occurredAtMillis,
            'store:' || s.storeId AS establishmentId,
            st.name AS establishmentName,
            l.name AS listName,
            s.budgetCents AS budgetCents,
            COALESCE((SELECT SUM(i.totalCents) FROM shopping_session_items i WHERE i.sessionId = s.id), 0) AS spentCents
        FROM shopping_sessions s
        LEFT JOIN stores st ON st.id = s.storeId
        LEFT JOIN shopping_lists l ON l.id = s.shoppingListId
        WHERE s.status = 'COMPLETED' AND s.budgetCents IS NOT NULL AND s.mode IN (:sessionModes)
            AND s.completedAt >= :fromMillis AND s.completedAt < :untilMillis
        ORDER BY s.completedAt
        """,
    )
    suspend fun getBudgets(fromMillis: Long, untilMillis: Long, sessionModes: List<String>): List<ReportBudgetDb>

    /** Compras concluídas sem itens (o total pode não refletir a compra real). */
    @Query(
        """
        SELECT COUNT(*) FROM shopping_sessions s
        WHERE s.status = 'COMPLETED' AND s.mode IN (:sessionModes)
            AND s.completedAt >= :fromMillis AND s.completedAt < :untilMillis
            AND NOT EXISTS (SELECT 1 FROM shopping_session_items i WHERE i.sessionId = s.id)
        """,
    )
    suspend fun countSessionsWithoutItems(fromMillis: Long, untilMillis: Long, sessionModes: List<String>): Int

    /** Compras concluídas cujo total gravado difere da soma dos itens. */
    @Query(
        """
        SELECT COUNT(*) FROM shopping_sessions s
        WHERE s.status = 'COMPLETED' AND s.mode IN (:sessionModes)
            AND s.completedAt >= :fromMillis AND s.completedAt < :untilMillis
            AND s.finalTotalCents IS NOT NULL
            AND s.finalTotalCents != COALESCE((SELECT SUM(i.totalCents) FROM shopping_session_items i WHERE i.sessionId = s.id), 0)
        """,
    )
    suspend fun countSessionTotalMismatches(fromMillis: Long, untilMillis: Long, sessionModes: List<String>): Int

    @Query(
        """
        SELECT COUNT(*) FROM expenses e
        WHERE e.occurredAt >= :fromMillis AND e.occurredAt < :untilMillis
            AND NOT EXISTS (SELECT 1 FROM expense_items i WHERE i.expenseId = e.id)
        """,
    )
    suspend fun countExpensesWithoutItems(fromMillis: Long, untilMillis: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM expenses e
        WHERE e.occurredAt >= :fromMillis AND e.occurredAt < :untilMillis
            AND e.totalCents != COALESCE((SELECT SUM(i.totalCents) FROM expense_items i WHERE i.expenseId = e.id), 0)
        """,
    )
    suspend fun countExpenseTotalMismatches(fromMillis: Long, untilMillis: Long): Int
}

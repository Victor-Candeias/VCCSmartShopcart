package pt.vcc.smartshopcart.domain.repository

import kotlinx.coroutines.flow.Flow
import pt.vcc.smartshopcart.domain.model.expense.Establishment
import pt.vcc.smartshopcart.domain.model.expense.ExpenseCategory
import pt.vcc.smartshopcart.domain.model.expense.ExpenseDetails
import pt.vcc.smartshopcart.domain.model.expense.ExpenseDraft
import pt.vcc.smartshopcart.domain.model.expense.ExpenseProduct
import pt.vcc.smartshopcart.domain.model.expense.ExpenseSummary

interface ExpenseRepository {
    fun observeCategories(includeInactive: Boolean = false): Flow<List<ExpenseCategory>>

    /** Produtos de [categoryId] (todos quando nulo), por ordem de apresentação. */
    fun observeProducts(categoryId: String?, includeInactive: Boolean = false): Flow<List<ExpenseProduct>>

    fun observeExpenses(): Flow<List<ExpenseSummary>>

    fun observeExpense(id: String): Flow<ExpenseDetails?>

    fun observeEstablishments(): Flow<List<Establishment>>

    /**
     * Grava cabeçalho, itens e total numa única transação. Com [expenseId] substitui os itens
     * da despesa existente, mantendo `createdAt`. Devolve o ID da despesa.
     */
    suspend fun saveExpense(draft: ExpenseDraft, expenseId: String? = null): String

    suspend fun getExpense(id: String): ExpenseDetails?

    suspend fun deleteExpense(id: String)

    suspend fun getProduct(id: String): ExpenseProduct?

    suspend fun upsertProduct(product: ExpenseProduct)

    suspend fun nextProductSortOrder(categoryId: String): Int

    /** Grava `sortOrder = índice` para os produtos indicados, de forma atómica. */
    suspend fun reorderProducts(orderedIds: List<String>)

    /** Insere categorias e produtos predefinidos ignorando IDs já existentes (nunca sobrescreve). */
    suspend fun importCatalog(categories: List<ExpenseCategory>, products: List<ExpenseProduct>)
}

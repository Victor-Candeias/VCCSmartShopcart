package pt.vcc.smartshopcart.domain.model.expense

/**
 * Modo Despesas Rápidas: catálogo de produtos/serviços habituais, independente dos produtos
 * identificados por EAN/GTIN (`products`) e das sessões de compra. Valores em cêntimos `Long`
 * e instantes em epoch millis UTC.
 */
data class ExpenseCategory(
    val id: String,
    val name: String,
    /** Emoji ou símbolo curto apresentado ao lado do nome. */
    val icon: String?,
    val sortOrder: Int,
    val isActive: Boolean,
)

data class ExpenseProduct(
    val id: String,
    val categoryId: String,
    val name: String,
    val icon: String?,
    val defaultPriceCents: Long?,
    val isFavorite: Boolean,
    val isActive: Boolean,
    val sortOrder: Int,
)

data class Establishment(
    val id: String,
    val name: String,
    val type: String?,
    val isFavorite: Boolean,
)

data class Expense(
    val id: String,
    val establishmentId: String?,
    val occurredAt: Long,
    val totalCents: Long,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** `nameSnapshot` e `unitPriceCents` preservam os valores históricos mesmo que o catálogo mude. */
data class ExpenseItem(
    val id: String,
    val expenseId: String,
    val productId: String?,
    val nameSnapshot: String,
    val quantity: Int,
    val unitPriceCents: Long,
    val totalCents: Long,
)

data class ExpenseSummary(
    val expense: Expense,
    val establishmentName: String?,
    val itemCount: Int,
    val categoryIds: Set<String>,
)

data class ExpenseDetails(
    val expense: Expense,
    val establishment: Establishment?,
    val items: List<ExpenseItem>,
)

/** Item validado pronto a gravar. */
data class ExpenseDraftItem(
    val productId: String?,
    val name: String,
    val quantity: Int,
    val unitPriceCents: Long,
) {
    val totalCents: Long
        get() = Math.multiplyExact(quantity.toLong(), unitPriceCents)
}

/**
 * Despesa validada pronta a gravar. O estabelecimento é indicado pelo nome e resolvido (ou
 * criado) pelo repositório na mesma transação.
 */
data class ExpenseDraft(
    val establishmentName: String?,
    val occurredAt: Long,
    val items: List<ExpenseDraftItem>,
    val notes: String? = null,
) {
    val totalCents: Long
        get() = items.fold(0L) { total, item -> Math.addExact(total, item.totalCents) }
}

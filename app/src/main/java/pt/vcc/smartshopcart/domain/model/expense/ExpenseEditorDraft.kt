package pt.vcc.smartshopcart.domain.model.expense

import kotlinx.serialization.Serializable

/**
 * Estado editável de uma despesa, persistido como rascunho local (§2.4) até ser guardado ou
 * cancelado. Os preços são anuláveis enquanto não forem definidos.
 */
@Serializable
data class ExpenseEditorDraft(
    /** Despesa existente a editar; nulo para uma nova despesa. */
    val editingExpenseId: String? = null,
    val establishmentName: String = "",
    val occurredAt: Long,
    val lines: List<ExpenseEditorLine> = emptyList(),
    val notes: String = "",
    val selectedCategoryId: String? = null,
) {
    /** Soma das linhas com preço definido. */
    val totalCents: Long
        get() = lines.fold(0L) { total, line -> Math.addExact(total, line.subtotalCents ?: 0L) }

    val missingPriceCount: Int
        get() = lines.count { it.unitPriceCents == null }

    /** Sem conteúdo relevante: não vale a pena guardar como rascunho. */
    val isBlank: Boolean
        get() = lines.isEmpty() && establishmentName.isBlank() && notes.isBlank()

    /**
     * Acrescenta o produto; se já estiver na despesa aumenta a quantidade (tocar duas vezes em
     * "Café" regista 2 cafés).
     */
    fun addProduct(product: ExpenseProduct, key: String, unitPriceCents: Long?): ExpenseEditorDraft {
        val existing = lines.firstOrNull { it.productId == product.id }
        return if (existing != null) {
            changeQuantity(existing.key, +1)
        } else {
            copy(lines = lines + ExpenseEditorLine(key, product.id, product.name, 1, unitPriceCents))
        }
    }

    fun changeQuantity(key: String, delta: Int): ExpenseEditorDraft = updateLine(key) { line ->
        line.copy(quantity = (line.quantity + delta).coerceIn(1, MAX_QUANTITY))
    }

    fun setPrice(key: String, unitPriceCents: Long): ExpenseEditorDraft {
        require(unitPriceCents >= 0) { "Preço negativo" }
        return updateLine(key) { it.copy(unitPriceCents = unitPriceCents) }
    }

    fun removeLine(key: String): ExpenseEditorDraft = copy(lines = lines.filterNot { it.key == key })

    private fun updateLine(key: String, change: (ExpenseEditorLine) -> ExpenseEditorLine) =
        copy(lines = lines.map { if (it.key == key) change(it) else it })

    companion object {
        const val MAX_QUANTITY = 999
    }
}

@Serializable
data class ExpenseEditorLine(
    /** Identificador local da linha (estável durante a edição). */
    val key: String,
    val productId: String?,
    val name: String,
    val quantity: Int = 1,
    val unitPriceCents: Long? = null,
) {
    val subtotalCents: Long?
        get() = unitPriceCents?.let { Math.multiplyExact(quantity.toLong(), it) }
}

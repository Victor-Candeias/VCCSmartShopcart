package pt.vcc.smartshopcart.domain.usecase.expense

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import pt.vcc.smartshopcart.core.common.IdProvider
import pt.vcc.smartshopcart.domain.model.expense.ExpenseCategory
import pt.vcc.smartshopcart.domain.model.expense.ExpenseProduct
import pt.vcc.smartshopcart.domain.repository.ExpenseRepository
import java.text.Normalizer
import javax.inject.Inject

data class ExpenseCatalog(
    val categories: List<ExpenseCategory>,
    /** Por categoria (ordem das categorias) e depois por `sortOrder`. */
    val products: List<ExpenseProduct>,
)

object ExpenseSearch {
    private val marks = Regex("\\p{Mn}+")

    /** Minúsculas e sem acentos, para pesquisar "galao" e encontrar "Galão". */
    fun normalize(text: String): String =
        marks.replace(Normalizer.normalize(text.trim(), Normalizer.Form.NFD), "").lowercase()

    /**
     * Filtra por categoria (todas quando nula) e por texto. Com [favoritesFirst] os favoritos
     * aparecem primeiro, mantendo a ordem relativa.
     */
    fun filter(
        products: List<ExpenseProduct>,
        categoryId: String?,
        query: String,
        favoritesFirst: Boolean = false,
    ): List<ExpenseProduct> {
        val needle = normalize(query)
        val filtered = products.filter { p ->
            (categoryId == null || p.categoryId == categoryId) && (needle.isEmpty() || normalize(p.name).contains(needle))
        }
        return if (favoritesFirst) filtered.sortedByDescending { it.isFavorite } else filtered
    }
}

class GetExpenseCatalogUseCase @Inject constructor(private val repository: ExpenseRepository) {
    operator fun invoke(includeHidden: Boolean = false): Flow<ExpenseCatalog> =
        combine(repository.observeCategories(), repository.observeProducts(null, includeHidden)) { categories, products ->
            val categoryOrder = categories.withIndex().associate { (index, c) -> c.id to index }
            ExpenseCatalog(
                categories = categories,
                products = products
                    .filter { it.categoryId in categoryOrder }
                    .sortedWith(compareBy({ categoryOrder.getValue(it.categoryId) }, { it.sortOrder })),
            )
        }
}

class SaveExpenseProductUseCase @Inject constructor(
    private val repository: ExpenseRepository,
    private val ids: IdProvider,
) {
    /** Cria (quando [id] é nulo) ou edita um produto, preservando favorito e ocultação. */
    suspend operator fun invoke(
        id: String?,
        categoryId: String,
        name: String,
        icon: String?,
        defaultPriceCents: Long?,
    ): ExpenseProduct {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Nome obrigatório" }
        require(defaultPriceCents == null || defaultPriceCents >= 0) { "Preço habitual inválido" }
        val existing = id?.let { repository.getProduct(it) }
        require(id == null || existing != null) { "Produto inexistente" }
        val sortOrder = if (existing != null && existing.categoryId == categoryId) {
            existing.sortOrder
        } else {
            repository.nextProductSortOrder(categoryId)
        }
        val product = ExpenseProduct(
            id = existing?.id ?: ids.newId(),
            categoryId = categoryId,
            name = cleanName,
            icon = icon?.trim()?.takeIf { it.isNotEmpty() },
            defaultPriceCents = defaultPriceCents,
            isFavorite = existing?.isFavorite ?: false,
            isActive = existing?.isActive ?: true,
            sortOrder = sortOrder,
        )
        repository.upsertProduct(product)
        return product
    }
}

class SetExpenseProductFavoriteUseCase @Inject constructor(private val repository: ExpenseRepository) {
    suspend operator fun invoke(productId: String, favorite: Boolean) {
        val product = repository.getProduct(productId) ?: return
        if (product.isFavorite != favorite) repository.upsertProduct(product.copy(isFavorite = favorite))
    }
}

/** Ocultar mantém o produto (e o histórico que o referencia); apenas deixa de ser sugerido. */
class SetExpenseProductActiveUseCase @Inject constructor(private val repository: ExpenseRepository) {
    suspend operator fun invoke(productId: String, active: Boolean) {
        val product = repository.getProduct(productId) ?: return
        if (product.isActive != active) repository.upsertProduct(product.copy(isActive = active))
    }
}

class MoveExpenseProductUseCase @Inject constructor(private val repository: ExpenseRepository) {
    /**
     * Troca o produto com o vizinho visível (acima ou abaixo) da mesma categoria. Com
     * [includeHidden] falso os ocultos são ignorados na escolha do vizinho mas mantêm a posição.
     * Devolve `false` quando já está no limite.
     */
    suspend operator fun invoke(productId: String, up: Boolean, includeHidden: Boolean = false): Boolean {
        val product = repository.getProduct(productId) ?: return false
        val all = repository.observeProducts(product.categoryId, includeInactive = true).first().toMutableList()
        val visible = all.filter { includeHidden || it.isActive }
        val index = visible.indexOfFirst { it.id == productId }
        val neighbor = visible.getOrNull(if (up) index - 1 else index + 1) ?: return false
        val a = all.indexOfFirst { it.id == productId }
        val b = all.indexOfFirst { it.id == neighbor.id }
        all[a] = neighbor
        all[b] = product
        repository.reorderProducts(all.map { it.id })
        return true
    }
}

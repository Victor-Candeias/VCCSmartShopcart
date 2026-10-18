package pt.vcc.smartshopcart.domain.usecase.expense

import pt.vcc.smartshopcart.domain.model.expense.ExpenseEditorDraft
import pt.vcc.smartshopcart.domain.model.expense.PriceSuggestion
import pt.vcc.smartshopcart.domain.model.expense.PriceSuggestionSource
import pt.vcc.smartshopcart.domain.repository.ExpenseRepository
import javax.inject.Inject

/**
 * Sugere o preço de um produto (§7): último preço pago no mesmo estabelecimento → último preço
 * pago em qualquer lado → preço habitual do catálogo → [fallbackCents] → vazio. A sugestão nunca é
 * um preço garantido: fica marcada como tal no editor até o utilizador a alterar ou guardar.
 */
class SuggestProductPriceUseCase @Inject constructor(private val repository: ExpenseRepository) {

    suspend operator fun invoke(
        productId: String?,
        establishmentName: String?,
        fallbackCents: Long? = null,
    ): PriceSuggestion {
        if (productId != null) {
            val place = establishmentName?.trim()?.takeIf { it.isNotEmpty() }
            place?.let { repository.lastPaidPrice(productId, it) }
                ?.let { return PriceSuggestion(it, PriceSuggestionSource.SAME_ESTABLISHMENT) }
            repository.lastPaidPrice(productId)?.let { return PriceSuggestion(it, PriceSuggestionSource.LAST_PAID) }
            repository.getProduct(productId)?.defaultPriceCents
                ?.let { return PriceSuggestion(it, PriceSuggestionSource.CATALOG) }
        }
        return fallbackCents?.let { PriceSuggestion(it, PriceSuggestionSource.LAST_PAID) } ?: PriceSuggestion.NONE
    }

    /** Novas sugestões para as linhas que ainda as aceitam (ex.: depois de mudar o estabelecimento). */
    suspend fun suggestionsFor(draft: ExpenseEditorDraft): Map<String, PriceSuggestion> =
        draft.lines.filter { it.acceptsSuggestion }
            .associate { it.key to invoke(it.productId, draft.establishmentName) }
}

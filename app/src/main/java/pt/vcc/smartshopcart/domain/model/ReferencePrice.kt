package pt.vcc.smartshopcart.domain.model

import java.time.LocalDate

/** Base do preço comunitário (OP7): por embalagem ou por quilograma; nunca se comparam entre si. */
enum class PriceBasis { UNIT, KILOGRAM }

/**
 * Preço de referência observado pela comunidade Open Prices numa loja em Portugal (OP3). Nunca é o preço do
 * carrinho: serve apenas de referência até o utilizador o confirmar.
 */
data class ReferencePrice(
    val externalId: Long,
    val barcode: String,
    val priceCents: Long,
    val currency: String = "EUR",
    val storeName: String?,
    val storeCity: String?,
    val storeCountryCode: String,
    val locationId: Long?,
    val observedAt: LocalDate,
    val isDiscounted: Boolean,
    val discountType: String?,
    val priceWithoutDiscountCents: Long?,
    val basis: PriceBasis,
) {
    /** Observação antiga: mostrada como preço histórico (OP8). */
    fun isHistorical(today: LocalDate): Boolean = observedAt.isBefore(today.minusDays(HISTORICAL_AFTER_DAYS))

    companion object {
        const val HISTORICAL_AFTER_DAYS = 90L
    }
}

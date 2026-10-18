package pt.vcc.smartshopcart.domain.model

import java.time.LocalDate

/** Base do preço comunitário (OP7): por embalagem ou por quilograma; nunca se comparam entre si. */
enum class PriceBasis { UNIT, KILOGRAM }

/** Resultado da última consulta Open Prices de um EAN (OP9–OP12). */
enum class ReferenceSyncStatus {
    /** Há preços portugueses válidos. */
    SUCCESS,

    /** Consulta feita, mas sem preços portugueses válidos (fica em cache como as restantes). */
    EMPTY,

    /** HTTP 429: não consultar até `retryAfter`. */
    RATE_LIMITED,

    /** Erro de rede ou resposta inválida; a cache anterior mantém-se. */
    FAILED,
}

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

/** Estado da última consulta Open Prices de um EAN (OP10–OP12). */
data class ReferenceSyncState(
    val lastAttemptAt: Long,
    val lastSuccessAt: Long?,
    val status: ReferenceSyncStatus,
    val totalAvailable: Int,
    val incomplete: Boolean,
    val retryAfter: Long?,
)

/** Preços de referência em cache para um EAN, do mais recente para o mais antigo, e o estado da última consulta. */
data class ReferencePrices(
    val prices: List<ReferencePrice>,
    val sync: ReferenceSyncState?,
)

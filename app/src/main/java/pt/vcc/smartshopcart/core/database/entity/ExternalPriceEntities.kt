package pt.vcc.smartshopcart.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import pt.vcc.smartshopcart.domain.model.PriceBasis
import pt.vcc.smartshopcart.domain.model.ReferenceSyncStatus

/**
 * Preço comunitário Open Prices (OP9), separado do histórico local de preços confirmados
 * (`product_price_history`). `barcode` é sempre texto para preservar zeros à esquerda.
 */
@Entity(
    tableName = "external_prices",
    indices = [Index("barcode", "observedAt")],
)
data class ExternalPriceEntity(
    @PrimaryKey val externalId: Long,
    val barcode: String,
    val priceCents: Long,
    val currency: String,
    val storeName: String?,
    val storeCity: String?,
    val storeCountryCode: String,
    val locationId: Long?,
    /** Data ISO `yyyy-MM-dd`, ordenável como texto. */
    val observedAt: String,
    val isDiscounted: Boolean,
    val discountType: String?,
    val priceWithoutDiscountCents: Long?,
    val basis: PriceBasis,
    val fetchedAt: Long,
)

/** Estado da última consulta de um EAN, incluindo consultas vazias, paginação incompleta e 429 (OP9–OP12). */
@Entity(tableName = "external_price_sync")
data class ExternalPriceSyncEntity(
    @PrimaryKey val barcode: String,
    val lastAttemptAt: Long,
    val lastSuccessAt: Long?,
    val status: ReferenceSyncStatus,
    /** Total de observações anunciado pela API (todas as moedas/países filtrados pela consulta). */
    val totalAvailable: Int,
    /** A paginação foi limitada e podem faltar observações (OP11). */
    val incomplete: Boolean,
    /** Epoch millis até ao qual não se deve consultar a API (HTTP 429). */
    val retryAfter: Long?,
)

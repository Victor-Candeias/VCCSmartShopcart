package pt.vcc.smartshopcart.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.vcc.smartshopcart.domain.model.MeasureUnit
import pt.vcc.smartshopcart.domain.model.ProductPrice
import pt.vcc.smartshopcart.domain.model.SessionStatus
import pt.vcc.smartshopcart.domain.repository.ProductRepository
import pt.vcc.smartshopcart.domain.repository.ShoppingSessionRepository
import javax.inject.Inject

/** D23: só compras concluídas podem ser eliminadas; o histórico de preços mantém-se. */
class DeletePurchaseUseCase @Inject constructor(
    private val sessions: ShoppingSessionRepository,
) {
    /** Devolve `false` se a compra não existir ou não estiver concluída. */
    suspend operator fun invoke(sessionId: String): Boolean {
        if (sessions.getSession(sessionId)?.status != SessionStatus.COMPLETED) return false
        sessions.deleteSession(sessionId)
        return true
    }
}

/** Resumo dos preços de um produto num supermercado, na mesma unidade de preço. */
data class StorePriceSummary(
    val storeName: String?,
    val unit: MeasureUnit,
    val last: ProductPrice,
    val minCents: Long,
    val maxCents: Long,
    val count: Int,
)

object PriceHistorySummary {
    /** Agrupa por supermercado e unidade; o grupo com o preço mais recente vem primeiro. */
    fun byStore(prices: List<ProductPrice>): List<StorePriceSummary> =
        prices.groupBy { it.storeName to it.unit }
            .map { (key, group) ->
                StorePriceSummary(
                    storeName = key.first,
                    unit = key.second,
                    last = group.maxBy { it.observedAt },
                    minCents = group.minOf { it.priceCents },
                    maxCents = group.maxOf { it.priceCents },
                    count = group.size,
                )
            }
            .sortedByDescending { it.last.observedAt }
}

class ObservePriceHistoryUseCase @Inject constructor(
    private val products: ProductRepository,
) {
    operator fun invoke(barcode: String): Flow<List<StorePriceSummary>> =
        products.observePriceHistory(barcode).map(PriceHistorySummary::byStore)
}

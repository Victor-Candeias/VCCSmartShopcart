package pt.vcc.smartshopcart.domain.repository

import kotlinx.coroutines.flow.Flow
import pt.vcc.smartshopcart.domain.model.ReferencePrices

/** Resultado de um pedido de atualização dos preços de referência de um EAN. */
sealed interface ReferenceRefreshResult {
    /** A cache tem menos de 24 h; não houve chamada à API (OP10). */
    data object UpToDate : ReferenceRefreshResult

    /** Consulta concluída; `count == 0` significa que não há preços portugueses válidos. */
    data class Updated(val count: Int, val incomplete: Boolean) : ReferenceRefreshResult

    /** HTTP 429: não há novas consultas até [until] (epoch millis) (OP12). */
    data class RateLimited(val until: Long) : ReferenceRefreshResult

    /** Sem rede: apenas a cache local está disponível. */
    data object Offline : ReferenceRefreshResult

    /** Erro de rede ou resposta inválida; a cache anterior mantém-se. */
    data object Failed : ReferenceRefreshResult

    /** Código interno de loja ou inválido: nunca é consultado (OP13). */
    data object Unsupported : ReferenceRefreshResult
}

/** Preços de referência comunitários (Open Prices) com cache local (OP3, OP9–OP13). */
interface ReferencePriceRepository {
    fun observe(barcode: String): Flow<ReferencePrices>

    /** Atualiza a cache se tiver expirado (ou sempre, com [force]), respeitando limites 429. */
    suspend fun refresh(barcode: String, force: Boolean = false): ReferenceRefreshResult
}

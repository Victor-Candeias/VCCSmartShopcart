package pt.vcc.smartshopcart.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pt.vcc.smartshopcart.core.barcode.Barcodes
import pt.vcc.smartshopcart.core.common.TimeProvider
import pt.vcc.smartshopcart.core.database.dao.ExternalPriceDao
import pt.vcc.smartshopcart.core.database.entity.ExternalPriceEntity
import pt.vcc.smartshopcart.core.database.entity.ExternalPriceSyncEntity
import pt.vcc.smartshopcart.core.di.IoDispatcher
import pt.vcc.smartshopcart.core.network.NetworkMonitor
import pt.vcc.smartshopcart.data.remote.openprices.OpenPriceDto
import pt.vcc.smartshopcart.data.remote.openprices.OpenPriceLocationDto
import pt.vcc.smartshopcart.data.remote.openprices.OpenPricesApi
import pt.vcc.smartshopcart.data.remote.openprices.toReferencePrice
import pt.vcc.smartshopcart.domain.model.ReferencePrice
import pt.vcc.smartshopcart.domain.model.ReferencePrices
import pt.vcc.smartshopcart.domain.model.ReferenceSyncState
import pt.vcc.smartshopcart.domain.model.ReferenceSyncStatus
import pt.vcc.smartshopcart.domain.repository.ReferencePriceRepository
import pt.vcc.smartshopcart.domain.repository.ReferenceRefreshResult
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preços de referência Open Prices com cache Room (OP9–OP13): 24 h por EAN (também para resultados vazios),
 * até 3 páginas × 100, localizações em falta resolvidas uma vez, `Retry-After` respeitado e uma única
 * atualização de cada vez por EAN.
 */
@Singleton
class OpenPricesRepository @Inject constructor(
    private val api: OpenPricesApi,
    private val dao: ExternalPriceDao,
    private val time: TimeProvider,
    private val network: NetworkMonitor,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : ReferencePriceRepository {

    private val locks = ConcurrentHashMap<String, Mutex>()
    private val locations = ConcurrentHashMap<Long, OpenPriceLocationDto>()

    override fun observe(barcode: String): Flow<ReferencePrices> =
        combine(dao.observePrices(barcode), dao.observeSync(barcode)) { prices, sync ->
            ReferencePrices(prices.mapNotNull { it.toDomain() }, sync?.toDomain())
        }

    override suspend fun refresh(barcode: String, force: Boolean): ReferenceRefreshResult = withContext(io) {
        if (!isQueryable(barcode)) return@withContext ReferenceRefreshResult.Unsupported
        locks.getOrPut(barcode) { Mutex() }.withLock { refreshLocked(barcode, force) }
    }

    private suspend fun refreshLocked(barcode: String, force: Boolean): ReferenceRefreshResult {
        val now = time.now()
        val previous = dao.getSync(barcode)
        previous?.retryAfter?.let { until -> if (now < until) return ReferenceRefreshResult.RateLimited(until) }
        val lastSuccess = previous?.lastSuccessAt
        if (!force && lastSuccess != null && now - lastSuccess in 0 until CACHE_TTL_MS) return ReferenceRefreshResult.UpToDate
        if (!network.isOnline()) return ReferenceRefreshResult.Offline

        return when (val fetched = fetch(barcode)) {
            is Fetch.Success -> store(barcode, fetched, now)
            is Fetch.RateLimited -> {
                dao.upsertSync(failedSync(barcode, previous, now, ReferenceSyncStatus.RATE_LIMITED, fetched.until))
                ReferenceRefreshResult.RateLimited(fetched.until)
            }
            Fetch.Failed -> {
                dao.upsertSync(failedSync(barcode, previous, now, ReferenceSyncStatus.FAILED, retryAfter = null))
                ReferenceRefreshResult.Failed
            }
        }
    }

    private suspend fun fetch(barcode: String): Fetch = try {
        val items = mutableListOf<OpenPriceDto>()
        var total = 0
        var outcome: Fetch? = null
        for (page in 1..MAX_PAGES) {
            val response = api.getPrices(barcode, page = page)
            if (response.code() == HTTP_TOO_MANY_REQUESTS) {
                outcome = Fetch.RateLimited(retryAfterUntil(response.headers()["Retry-After"], time.now()))
                break
            }
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                outcome = Fetch.Failed
                break
            }
            items += body.items
            total = maxOf(total, body.total)
            if (body.items.isEmpty() || page >= body.pages) break
        }
        outcome ?: Fetch.Success(items, total)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        Fetch.Failed
    }

    private suspend fun store(barcode: String, fetched: Fetch.Success, now: Long): ReferenceRefreshResult {
        val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        val missingLocations = mutableSetOf<Long>()
        val prices = fetched.items.mapNotNull { dto ->
            dto.toReferencePrice(barcode, today) ?: dto.locationId
                ?.takeIf { dto.location == null }
                ?.let { id -> resolveLocation(id, missingLocations) }
                ?.let { location -> dto.toReferencePrice(barcode, today, location) }
        }.distinctBy { it.externalId }

        val incomplete = fetched.total > fetched.items.size
        val sync = ExternalPriceSyncEntity(
            barcode = barcode,
            lastAttemptAt = now,
            lastSuccessAt = now,
            status = if (prices.isEmpty()) ReferenceSyncStatus.EMPTY else ReferenceSyncStatus.SUCCESS,
            totalAvailable = fetched.total,
            incomplete = incomplete,
            retryAfter = null,
        )
        dao.replacePrices(barcode, prices.map { it.toEntity(now) }, sync)
        dao.purgeOlderThan(now - RETENTION_MS)
        return ReferenceRefreshResult.Updated(prices.size, incomplete)
    }

    /** Resolve uma localização que não veio embebida; falhas não são repetidas na mesma atualização. */
    private suspend fun resolveLocation(id: Long, missing: MutableSet<Long>): OpenPriceLocationDto? {
        locations[id]?.let { return it }
        if (id in missing) return null
        val location = try {
            api.getLocation(id).takeIf { it.isSuccessful }?.body()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (location == null) missing += id else locations[id] = location
        return location
    }

    private fun failedSync(
        barcode: String,
        previous: ExternalPriceSyncEntity?,
        now: Long,
        status: ReferenceSyncStatus,
        retryAfter: Long?,
    ) = previous?.copy(lastAttemptAt = now, status = status, retryAfter = retryAfter)
        ?: ExternalPriceSyncEntity(barcode, now, null, status, totalAvailable = 0, incomplete = false, retryAfter = retryAfter)

    private sealed interface Fetch {
        data class Success(val items: List<OpenPriceDto>, val total: Int) : Fetch
        data class RateLimited(val until: Long) : Fetch
        data object Failed : Fetch
    }

    companion object {
        const val CACHE_TTL_MS = 24L * 60 * 60 * 1000
        const val RETENTION_MS = 90L * 24 * 60 * 60 * 1000
        const val DEFAULT_RETRY_AFTER_MS = 60_000L
        const val MAX_RETRY_AFTER_MS = 24L * 60 * 60 * 1000
        const val MAX_PAGES = 3
        private const val HTTP_TOO_MANY_REQUESTS = 429

        /** Códigos internos de loja (D7) e valores que não são EAN/UPC nunca são enviados à API (OP13). */
        fun isQueryable(barcode: String): Boolean =
            barcode.length in 8..14 && barcode.all { it in '0'..'9' } && !Barcodes.isInternalStoreCode(barcode)

        /** `Retry-After` em segundos ou data HTTP; por omissão 60 s, limitado a 24 h (OP12). */
        fun retryAfterUntil(header: String?, now: Long): Long {
            val value = header?.trim().orEmpty()
            val until = value.toLongOrNull()?.takeIf { it > 0 }?.let { now + it * 1000 }
                ?: runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
                    .getOrNull()?.takeIf { it > now }
                ?: (now + DEFAULT_RETRY_AFTER_MS)
            return minOf(until, now + MAX_RETRY_AFTER_MS)
        }
    }
}

private fun ExternalPriceEntity.toDomain(): ReferencePrice? {
    val date = runCatching { LocalDate.parse(observedAt) }.getOrNull() ?: return null
    return ReferencePrice(
        externalId = externalId,
        barcode = barcode,
        priceCents = priceCents,
        currency = currency,
        storeName = storeName,
        storeCity = storeCity,
        storeCountryCode = storeCountryCode,
        locationId = locationId,
        observedAt = date,
        isDiscounted = isDiscounted,
        discountType = discountType,
        priceWithoutDiscountCents = priceWithoutDiscountCents,
        basis = basis,
    )
}

private fun ReferencePrice.toEntity(fetchedAt: Long) = ExternalPriceEntity(
    externalId = externalId,
    barcode = barcode,
    priceCents = priceCents,
    currency = currency,
    storeName = storeName,
    storeCity = storeCity,
    storeCountryCode = storeCountryCode,
    locationId = locationId,
    observedAt = observedAt.toString(),
    isDiscounted = isDiscounted,
    discountType = discountType,
    priceWithoutDiscountCents = priceWithoutDiscountCents,
    basis = basis,
    fetchedAt = fetchedAt,
)

private fun ExternalPriceSyncEntity.toDomain() = ReferenceSyncState(
    lastAttemptAt = lastAttemptAt,
    lastSuccessAt = lastSuccessAt,
    status = status,
    totalAvailable = totalAvailable,
    incomplete = incomplete,
    retryAfter = retryAfter,
)

package pt.vcc.smartshopcart.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pt.vcc.smartshopcart.core.database.ShoppingDatabase
import pt.vcc.smartshopcart.data.remote.openprices.OpenPricesClient
import pt.vcc.smartshopcart.domain.model.PriceBasis
import pt.vcc.smartshopcart.domain.model.ReferenceSyncStatus
import pt.vcc.smartshopcart.domain.repository.ReferenceRefreshResult
import java.time.Instant
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class OpenPricesRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var db: ShoppingDatabase
    private lateinit var repository: OpenPricesRepository
    private var now = Instant.parse("2026-02-01T12:00:00Z").toEpochMilli()
    private var online = true

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ShoppingDatabase::class.java).allowMainThreadQueries().build()
        val api = OpenPricesClient.api(OpenPricesClient.okHttp("test"), server.url("/api/v1/").toString())
        repository = OpenPricesRepository(api, db.externalPriceDao(), { now }, { online }, Dispatchers.IO)
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
    }

    @Test
    fun `guarda apenas precos portugueses e usa a cache durante 24 horas`() = runTest {
        server.enqueue(page(price(1, 1.29), price(2, 2.0, country = "FR"), price(3, 4.99, pricePer = "KILOGRAM")))

        assertEquals(ReferenceRefreshResult.Updated(2, incomplete = false), repository.refresh(EAN))
        now += OpenPricesRepository.CACHE_TTL_MS - 1
        assertEquals(ReferenceRefreshResult.UpToDate, repository.refresh(EAN))

        assertEquals(1, server.requestCount)
        val cached = repository.observe(EAN).first()
        assertEquals(listOf(129L, 499L), cached.prices.map { it.priceCents }.sorted())
        assertEquals(PriceBasis.KILOGRAM, cached.prices.single { it.externalId == 3L }.basis)
        assertEquals(ReferenceSyncStatus.SUCCESS, cached.sync?.status)
    }

    @Test
    fun `resultado vazio tambem fica em cache`() = runTest {
        server.enqueue(page())

        assertEquals(ReferenceRefreshResult.Updated(0, incomplete = false), repository.refresh(EAN))
        assertEquals(ReferenceRefreshResult.UpToDate, repository.refresh(EAN))

        assertEquals(1, server.requestCount)
        assertEquals(ReferenceSyncStatus.EMPTY, repository.observe(EAN).first().sync?.status)
    }

    @Test
    fun `cache expirada ou atualizacao forcada voltam a consultar e substituem precos`() = runTest {
        server.enqueue(page(price(1, 1.29)))
        server.enqueue(page(price(2, 1.35)))
        server.enqueue(page(price(3, 1.40)))

        repository.refresh(EAN)
        now += OpenPricesRepository.CACHE_TTL_MS
        repository.refresh(EAN)
        assertEquals(listOf(2L), repository.observe(EAN).first().prices.map { it.externalId })

        repository.refresh(EAN, force = true)
        assertEquals(listOf(3L), repository.observe(EAN).first().prices.map { it.externalId })
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `pagina no maximo 3 vezes e marca resultado incompleto`() = runTest {
        repeat(3) { i -> server.enqueue(page(price(i + 1L, 1.0 + i), pages = 5, total = 500)) }

        val result = repository.refresh(EAN)

        assertEquals(ReferenceRefreshResult.Updated(3, incomplete = true), result)
        assertEquals(3, server.requestCount)
        assertEquals(listOf("1", "2", "3"), (1..3).map { server.takeRequest().requestUrl!!.queryParameter("page") })
        val sync = repository.observe(EAN).first().sync!!
        assertTrue(sync.incomplete)
        assertEquals(500, sync.totalAvailable)
    }

    @Test
    fun `http 429 respeita Retry-After e preserva a cache`() = runTest {
        server.enqueue(page(price(1, 1.29)))
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "120"))
        repository.refresh(EAN)
        now += OpenPricesRepository.CACHE_TTL_MS + 1

        val until = now + 120_000
        assertEquals(ReferenceRefreshResult.RateLimited(until), repository.refresh(EAN))

        now += 60_000
        assertEquals(ReferenceRefreshResult.RateLimited(until), repository.refresh(EAN, force = true))
        assertEquals(2, server.requestCount)
        val cached = repository.observe(EAN).first()
        assertEquals(listOf(1L), cached.prices.map { it.externalId })
        assertEquals(ReferenceSyncStatus.RATE_LIMITED, cached.sync?.status)

        now = until
        server.enqueue(page(price(2, 1.30)))
        assertEquals(ReferenceRefreshResult.Updated(1, incomplete = false), repository.refresh(EAN))
        assertNull(repository.observe(EAN).first().sync?.retryAfter)
    }

    @Test
    fun `Retry-After aceita data HTTP e usa 60 segundos por omissao`() {
        val base = Instant.parse("2026-02-01T12:00:00Z").toEpochMilli()
        assertEquals(base + 30_000, OpenPricesRepository.retryAfterUntil("Sun, 01 Feb 2026 12:00:30 GMT", base))
        assertEquals(base + 60_000, OpenPricesRepository.retryAfterUntil(null, base))
        assertEquals(base + 60_000, OpenPricesRepository.retryAfterUntil("abc", base))
        assertEquals(base + OpenPricesRepository.MAX_RETRY_AFTER_MS, OpenPricesRepository.retryAfterUntil("999999", base))
    }

    @Test
    fun `erro de rede preserva a cache anterior`() = runTest {
        server.enqueue(page(price(1, 1.29)))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        repository.refresh(EAN)

        assertEquals(ReferenceRefreshResult.Failed, repository.refresh(EAN, force = true))

        val cached = repository.observe(EAN).first()
        assertEquals(listOf(1L), cached.prices.map { it.externalId })
        assertEquals(ReferenceSyncStatus.FAILED, cached.sync?.status)
    }

    @Test
    fun `sem rede ou com codigo interno nao chama a API`() = runTest {
        online = false
        assertEquals(ReferenceRefreshResult.Offline, repository.refresh(EAN))
        online = true
        assertEquals(ReferenceRefreshResult.Unsupported, repository.refresh("2001234567894"))
        assertEquals(ReferenceRefreshResult.Unsupported, repository.refresh("abc"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `resolve localizacao em falta uma unica vez`() = runTest {
        server.enqueue(page(price(1, 1.29, embedLocation = false), price(2, 1.31, embedLocation = false)))
        server.enqueue(MockResponse().setBody(LOCATION_PT))

        assertEquals(ReferenceRefreshResult.Updated(2, incomplete = false), repository.refresh(EAN))

        assertEquals(2, server.requestCount)
        server.takeRequest()
        assertTrue(server.takeRequest().path!!.endsWith("/locations/33"))
        assertEquals("Pingo Doce", repository.observe(EAN).first().prices.first().storeName)
    }

    @Test
    fun `atualizacoes concorrentes do mesmo EAN fazem uma unica chamada`() = runTest {
        server.enqueue(page(price(1, 1.29)).setBodyDelay(200, TimeUnit.MILLISECONDS))

        val results = (1..3).map { async { repository.refresh(EAN) } }.awaitAll()

        assertEquals(1, server.requestCount)
        assertEquals(1, results.count { it is ReferenceRefreshResult.Updated })
        assertEquals(2, results.count { it == ReferenceRefreshResult.UpToDate })
        assertFalse(repository.observe(EAN).first().prices.isEmpty())
    }

    private fun page(vararg items: String, pages: Int = 1, total: Int = items.size) = MockResponse().setBody(
        """{"items":[${items.joinToString(",")}],"page":1,"pages":$pages,"size":100,"total":$total}""",
    )

    private fun price(
        id: Long,
        value: Double,
        country: String = "PT",
        pricePer: String? = null,
        embedLocation: Boolean = true,
    ): String {
        val location = if (embedLocation) {
            ""","location":{"id":33,"type":"OSM","osm_brand":"Continente","osm_address_city":"Lisboa","osm_address_country_code":"$country"}"""
        } else {
            ""
        }
        val per = pricePer?.let { "\"$it\"" } ?: "null"
        return """{"id":$id,"type":"PRODUCT","product_code":"$EAN","location_id":33,"price":$value,"currency":"EUR",""" +
            """"date":"2026-01-${10 + id % 10}","price_per":$per,"price_is_discounted":false$location}"""
    }

    private companion object {
        const val EAN = "5601234567890"
        const val LOCATION_PT =
            """{"id":33,"type":"OSM","osm_brand":"Pingo Doce","osm_address_city":"Lisboa","osm_address_country_code":"PT"}"""
    }
}

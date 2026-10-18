package pt.vcc.smartshopcart.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pt.vcc.smartshopcart.core.database.entity.ExternalPriceEntity
import pt.vcc.smartshopcart.core.database.entity.ExternalPriceSyncEntity
import pt.vcc.smartshopcart.domain.model.PriceBasis
import pt.vcc.smartshopcart.domain.model.ReferenceSyncStatus

@RunWith(RobolectricTestRunner::class)
class ExternalPriceDaoTest {

    private lateinit var db: ShoppingDatabase
    private val dao get() = db.externalPriceDao()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ShoppingDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `mantem zeros a esquerda e nao duplica o mesmo id externo`() = runTest {
        dao.upsertAll(listOf(price(1, "0001234567890", 100), price(1, "0001234567890", 120)))

        val stored = dao.getPrices("0001234567890")
        assertEquals(1, stored.size)
        assertEquals(120L, stored.single().priceCents)
        assertTrue(dao.getPrices("1234567890").isEmpty())
    }

    @Test
    fun `devolve precos do mais recente para o mais antigo`() = runTest {
        dao.upsertAll(
            listOf(
                price(1, EAN, 100, observedAt = "2026-01-10"),
                price(2, EAN, 110, observedAt = "2026-03-01"),
                price(3, EAN, 105, observedAt = "2025-12-31"),
            ),
        )

        assertEquals(listOf(2L, 1L, 3L), dao.observePrices(EAN).first().map { it.externalId })
    }

    @Test
    fun `substituicao troca precos e estado do EAN sem tocar noutros`() = runTest {
        dao.upsertAll(listOf(price(1, EAN, 100), price(2, EAN, 110), price(9, OTHER, 50)))

        dao.replacePrices(EAN, listOf(price(3, EAN, 99)), sync(EAN, ReferenceSyncStatus.SUCCESS, at = 2_000))

        assertEquals(listOf(3L), dao.getPrices(EAN).map { it.externalId })
        assertEquals(listOf(9L), dao.getPrices(OTHER).map { it.externalId })
        assertEquals(ReferenceSyncStatus.SUCCESS, dao.getSync(EAN)?.status)
    }

    @Test
    fun `consulta vazia fica registada e limpa precos antigos`() = runTest {
        dao.upsertAll(listOf(price(1, EAN, 100)))

        dao.replacePrices(EAN, emptyList(), sync(EAN, ReferenceSyncStatus.EMPTY, at = 2_000))

        assertTrue(dao.getPrices(EAN).isEmpty())
        assertEquals(ReferenceSyncStatus.EMPTY, dao.observeSync(EAN).first()?.status)
    }

    @Test
    fun `purga remove apenas registos antigos`() = runTest {
        dao.upsertAll(listOf(price(1, EAN, 100, fetchedAt = 1_000), price(2, OTHER, 100, fetchedAt = 5_000)))
        dao.upsertSync(sync(EAN, ReferenceSyncStatus.SUCCESS, at = 1_000))
        dao.upsertSync(sync(OTHER, ReferenceSyncStatus.SUCCESS, at = 5_000))

        dao.purgeOlderThan(before = 3_000)

        assertTrue(dao.getPrices(EAN).isEmpty())
        assertNull(dao.getSync(EAN))
        assertEquals(1, dao.getPrices(OTHER).size)
        assertEquals(5_000L, dao.getSync(OTHER)?.lastAttemptAt)
    }

    private fun price(
        id: Long,
        barcode: String,
        cents: Long,
        observedAt: String = "2026-01-01",
        fetchedAt: Long = 1_000,
    ) = ExternalPriceEntity(
        externalId = id,
        barcode = barcode,
        priceCents = cents,
        currency = "EUR",
        storeName = "Continente",
        storeCity = "Lisboa",
        storeCountryCode = "PT",
        locationId = 7,
        observedAt = observedAt,
        isDiscounted = false,
        discountType = null,
        priceWithoutDiscountCents = null,
        basis = PriceBasis.UNIT,
        fetchedAt = fetchedAt,
    )

    private fun sync(barcode: String, status: ReferenceSyncStatus, at: Long) = ExternalPriceSyncEntity(
        barcode = barcode,
        lastAttemptAt = at,
        lastSuccessAt = at,
        status = status,
        totalAvailable = 0,
        incomplete = false,
        retryAfter = null,
    )

    private companion object {
        const val EAN = "5601234567890"
        const val OTHER = "5609876543210"
    }
}

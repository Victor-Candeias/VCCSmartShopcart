package pt.vcc.smartshopcart.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pt.vcc.smartshopcart.core.common.IdProvider
import pt.vcc.smartshopcart.core.common.TimeProvider
import pt.vcc.smartshopcart.core.database.ShoppingDatabase
import pt.vcc.smartshopcart.data.repository.RoomProductRepository
import pt.vcc.smartshopcart.data.repository.RoomShoppingSessionRepository
import pt.vcc.smartshopcart.data.repository.RoomStoreRepository
import pt.vcc.smartshopcart.domain.model.MeasureUnit
import pt.vcc.smartshopcart.domain.model.PriceSource
import pt.vcc.smartshopcart.domain.model.ProductPrice
import pt.vcc.smartshopcart.domain.model.SessionMode

@RunWith(RobolectricTestRunner::class)
class HistoryUseCasesTest {

    private lateinit var db: ShoppingDatabase
    private lateinit var sessions: RoomShoppingSessionRepository
    private var clock = 1_000L
    private var nextId = 0
    private val time = TimeProvider { clock++ }
    private val ids = IdProvider { "id-${nextId++}" }

    private lateinit var start: StartShoppingSessionUseCase
    private lateinit var add: AddSessionItemUseCase
    private lateinit var complete: CompleteShoppingSessionUseCase
    private lateinit var cancel: CancelShoppingSessionUseCase
    private lateinit var delete: DeletePurchaseUseCase
    private lateinit var priceHistory: ObservePriceHistoryUseCase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ShoppingDatabase::class.java).allowMainThreadQueries().build()
        sessions = RoomShoppingSessionRepository(db)
        start = StartShoppingSessionUseCase(sessions, RoomStoreRepository(db, ids, time), ids, time)
        add = AddSessionItemUseCase(sessions, ids, time)
        complete = CompleteShoppingSessionUseCase(sessions, ids, time)
        cancel = CancelShoppingSessionUseCase(sessions, time)
        delete = DeletePurchaseUseCase(sessions)
        priceHistory = ObservePriceHistoryUseCase(RoomProductRepository(db))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun purchase(store: String?, vararg prices: Pair<Long, String?>): String {
        val session = (start(SessionMode.CALCULATOR, storeName = store) as StartSessionResult.Started).session
        prices.forEach { (cents, barcode) -> add(session.id, NewSessionItem(unitPriceCents = cents, barcode = barcode)) }
        complete(session.id)
        return session.id
    }

    @Test
    fun `historico mostra so compras concluidas com total e itens, mais recente primeiro`() = runTest {
        val first = purchase("Continente", 150L to null, 250L to BARCODE)
        val cancelled = (start(SessionMode.CALCULATOR) as StartSessionResult.Started).session
        cancel(cancelled.id)
        val second = purchase(null, 99L to null)
        start(SessionMode.CALCULATOR)

        val history = sessions.observeCompletedSessions().first()
        assertEquals(listOf(second, first), history.map { it.session.id })
        assertEquals(400L, history[1].session.finalTotalCents)
        assertEquals(2, history[1].itemCount)
        assertEquals("Continente", history[1].storeName)
    }

    @Test
    fun `eliminar compra concluida remove itens e mantem historico de precos`() = runTest {
        val id = purchase("Continente", 250L to BARCODE)

        assertTrue(delete(id))
        assertNull(sessions.getSession(id))
        assertNull(sessions.observeSession(id).first())
        assertTrue(sessions.observeCompletedSessions().first().isEmpty())
        val prices = priceHistory(BARCODE).first()
        assertEquals(250L, prices.single().last.priceCents)
    }

    @Test
    fun `nao elimina compra ativa`() = runTest {
        val active = (start(SessionMode.CALCULATOR) as StartSessionResult.Started).session
        assertFalse(delete(active.id))
        assertFalse(delete("inexistente"))
        assertEquals(active, sessions.getSession(active.id))
    }

    @Test
    fun `historico de precos agrupado por supermercado`() = runTest {
        purchase("Continente", 250L to BARCODE)
        purchase("Pingo Doce", 239L to BARCODE)
        purchase("Continente", 199L to BARCODE)

        val summary = priceHistory(BARCODE).first()
        assertEquals(listOf("Continente", "Pingo Doce"), summary.map { it.storeName })
        with(summary.first()) {
            assertEquals(199L, last.priceCents)
            assertEquals(199L, minCents)
            assertEquals(250L, maxCents)
            assertEquals(2, count)
        }
    }

    @Test
    fun `precos por unidade diferente nao se misturam`() {
        val prices = listOf(
            ProductPrice(299, MeasureUnit.KG, "Lidl", 3, PriceSource.MANUAL),
            ProductPrice(150, MeasureUnit.UN, "Lidl", 2, PriceSource.OCR),
            ProductPrice(100, MeasureUnit.UN, "Lidl", 1, PriceSource.MANUAL),
        )
        val summary = PriceHistorySummary.byStore(prices)
        assertEquals(listOf(MeasureUnit.KG, MeasureUnit.UN), summary.map { it.unit })
        assertEquals(100L to 150L, summary[1].minCents to summary[1].maxCents)
    }

    private companion object {
        const val BARCODE = "5601234567890"
    }
}

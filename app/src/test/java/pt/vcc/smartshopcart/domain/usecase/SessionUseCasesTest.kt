package pt.vcc.smartshopcart.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pt.vcc.smartshopcart.core.common.IdProvider
import pt.vcc.smartshopcart.core.common.TimeProvider
import pt.vcc.smartshopcart.core.database.ShoppingDatabase
import pt.vcc.smartshopcart.data.repository.RoomShoppingSessionRepository
import pt.vcc.smartshopcart.data.repository.RoomStoreRepository
import pt.vcc.smartshopcart.domain.model.MeasureUnit
import pt.vcc.smartshopcart.domain.model.SessionMode
import pt.vcc.smartshopcart.domain.model.SessionStatus
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class SessionUseCasesTest {

    private lateinit var db: ShoppingDatabase
    private lateinit var sessions: RoomShoppingSessionRepository
    private var clock = 1_000L
    private var nextId = 0
    private val time = TimeProvider { clock++ }
    private val ids = IdProvider { "id-${nextId++}" }

    private lateinit var start: StartShoppingSessionUseCase
    private lateinit var add: AddSessionItemUseCase
    private lateinit var update: UpdateSessionItemUseCase
    private lateinit var remove: RemoveSessionItemUseCase
    private lateinit var restore: RestoreSessionItemUseCase
    private lateinit var complete: CompleteShoppingSessionUseCase
    private lateinit var cancel: CancelShoppingSessionUseCase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ShoppingDatabase::class.java).allowMainThreadQueries().build()
        sessions = RoomShoppingSessionRepository(db)
        val stores = RoomStoreRepository(db, ids, time)
        start = StartShoppingSessionUseCase(sessions, stores, ids, time)
        add = AddSessionItemUseCase(sessions, ids, time)
        update = UpdateSessionItemUseCase(sessions)
        remove = RemoveSessionItemUseCase(sessions)
        restore = RestoreSessionItemUseCase(sessions)
        complete = CompleteShoppingSessionUseCase(sessions, ids, time)
        cancel = CancelShoppingSessionUseCase(sessions, time)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun startCalculator(store: String? = null, budget: Long? = null) =
        (start(SessionMode.CALCULATOR, storeName = store, budgetCents = budget) as StartSessionResult.Started).session

    @Test
    fun `so pode existir uma sessao ativa`() = runTest {
        val first = startCalculator()
        val second = start(SessionMode.CALCULATOR)
        assertEquals(StartSessionResult.ActiveSessionExists(first), second)

        cancel(first.id)
        assertTrue(start(SessionMode.CALCULATOR) is StartSessionResult.Started)
    }

    @Test
    fun `supermercado e reutilizado sem distinguir maiusculas`() = runTest {
        val first = startCalculator(store = "Continente")
        cancel(first.id)
        val second = startCalculator(store = "  continente ")
        assertEquals(first.storeId, second.storeId)
    }

    @Test
    fun `dez precos manuais somados corretamente`() = runTest {
        val session = startCalculator(budget = 2000)
        val prices = listOf(149L, 89L, 1299L, 35L, 210L, 99L, 455L, 1L, 300L, 18L)
        prices.forEach { add(session.id, NewSessionItem(unitPriceCents = it)) }

        val loaded = sessions.observeSession(session.id).first()!!
        assertEquals(10, loaded.items.size)
        assertEquals(2655L, CalculateSessionTotalUseCase()(loaded.items))
    }

    @Test
    fun `editar recalcula o total da linha`() = runTest {
        val session = startCalculator()
        val item = add(session.id, NewSessionItem(unitPriceCents = 179))
        val updated = update(item.copy(quantity = BigDecimal("750"), unit = MeasureUnit.G, discountCents = 20))
        assertEquals(114L, updated.totalCents)
        assertEquals(114L, sessions.getItem(item.id)!!.totalCents)
    }

    @Test
    fun `remover e desfazer repoe a linha`() = runTest {
        val session = startCalculator()
        val item = add(session.id, NewSessionItem(unitPriceCents = 250, name = " Pão "))
        assertEquals("Pão", item.name)

        val removed = remove(item.id)
        assertNotNull(removed)
        assertNull(sessions.getItem(item.id))

        restore(removed!!)
        assertEquals(item, sessions.getItem(item.id))
    }

    @Test
    fun `concluir grava total e historico apenas de itens identificaveis`() = runTest {
        val session = startCalculator(store = "Pingo Doce")
        add(session.id, NewSessionItem(unitPriceCents = 100))
        add(session.id, NewSessionItem(unitPriceCents = 199, barcode = "5601234567890"))
        add(session.id, NewSessionItem(unitPriceCents = 300, quantity = BigDecimal("500"), unit = MeasureUnit.G, barcode = "5600000000001"))

        val completed = complete(session.id)
        assertEquals(SessionStatus.COMPLETED, completed.status)
        assertEquals(449L, completed.finalTotalCents)
        assertNotNull(completed.completedAt)

        val history = db.priceHistoryDao().lastForBarcode("5600000000001", session.storeId)!!
        assertEquals(MeasureUnit.KG, history.unit)
        assertEquals("0.500", history.quantity)
        assertNotNull(db.priceHistoryDao().lastForBarcode("5601234567890", session.storeId))
        assertNull(sessions.observeActiveSession().first())
    }

    @Test
    fun `sessao concluida rejeita alteracoes`() = runTest {
        val session = startCalculator()
        val item = add(session.id, NewSessionItem(unitPriceCents = 100, barcode = "5601234567890"))
        complete(session.id)

        assertThrows { add(session.id, NewSessionItem(unitPriceCents = 1)) }
        assertThrows { update(item.copy(unitPriceCents = 5)) }
        assertThrows { remove(item.id) }
        assertThrows { complete(session.id) }
        assertThrows { cancel(session.id) }
        assertEquals(100L, sessions.getSession(session.id)!!.finalTotalCents)
    }

    private suspend fun assertThrows(block: suspend () -> Unit) {
        val failed = try {
            block()
            false
        } catch (_: SessionNotActiveException) {
            true
        }
        assertTrue("Esperava SessionNotActiveException", failed)
    }
}

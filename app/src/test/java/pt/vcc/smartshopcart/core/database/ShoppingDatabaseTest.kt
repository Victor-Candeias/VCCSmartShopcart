package pt.vcc.smartshopcart.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pt.vcc.smartshopcart.core.database.entity.ProductPriceHistoryEntity
import pt.vcc.smartshopcart.core.database.entity.ShoppingListEntity
import pt.vcc.smartshopcart.core.database.entity.ShoppingListItemEntity
import pt.vcc.smartshopcart.core.database.entity.ShoppingSessionEntity
import pt.vcc.smartshopcart.core.database.entity.ShoppingSessionItemEntity
import pt.vcc.smartshopcart.domain.model.MeasureUnit
import pt.vcc.smartshopcart.domain.model.PriceSource
import pt.vcc.smartshopcart.domain.model.SessionMode
import pt.vcc.smartshopcart.domain.model.SessionStatus

@RunWith(RobolectricTestRunner::class)
class ShoppingDatabaseTest {

    private lateinit var db: ShoppingDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ShoppingDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `lista com itens e eliminada em cascata`() = runTest {
        val listDao = db.shoppingListDao()
        listDao.upsertList(ShoppingListEntity("l1", "Semana", 5000, 1, 1, false))
        listDao.upsertItem(ShoppingListItemEntity("i1", "l1", null, "Leite", "6", MeasureUnit.UN, 89, 0))
        listDao.upsertItem(ShoppingListItemEntity("i2", "l1", null, "Maçãs", "1.5", MeasureUnit.KG, null, 1))

        val lists = listDao.observeLists(archived = false).first()
        assertEquals(1, lists.size)
        assertEquals(2, lists.single().itemCount)
        assertEquals(2, listDao.nextSortOrder("l1"))

        listDao.deleteList("l1")
        assertEquals(0, listDao.getItems("l1").size)
    }

    @Test
    fun `sessao soma itens e mantem-se ativa ate concluir`() = runTest {
        val dao = db.shoppingSessionDao()
        dao.insertSession(session("s1"))
        dao.insertItem(item("a", "s1", 134))
        dao.insertItem(item("b", "s1", 299))

        assertEquals(433, dao.sumTotal("s1"))
        assertEquals("s1", dao.getLatestWithStatus(SessionStatus.ACTIVE)?.id)

        dao.upsertSession(session("s1").copy(status = SessionStatus.COMPLETED, completedAt = 2, finalTotalCents = 433))
        assertNull(dao.getLatestWithStatus(SessionStatus.ACTIVE))
        assertEquals(2, dao.observeSummaries(SessionStatus.COMPLETED).first().single().itemCount)
    }

    @Test
    fun `historico de precos ignora duplicados do mesmo item de sessao`() = runTest {
        db.shoppingSessionDao().insertSession(session("s1"))
        db.shoppingSessionDao().insertItem(item("a", "s1", 149))
        val entry = ProductPriceHistoryEntity("h1", null, "5601234567890", null, 149, MeasureUnit.UN, "1", PriceSource.MANUAL, 10, "a")

        db.priceHistoryDao().insertAll(listOf(entry))
        db.priceHistoryDao().insertAll(listOf(entry.copy(id = "h2", priceCents = 999)))

        assertEquals(149L, db.priceHistoryDao().lastForBarcode("5601234567890", null)?.priceCents)
    }

    private fun session(id: String) = ShoppingSessionEntity(
        id = id,
        shoppingListId = null,
        mode = SessionMode.CALCULATOR,
        status = SessionStatus.ACTIVE,
        storeId = null,
        budgetCents = null,
        startedAt = 1,
        completedAt = null,
        finalTotalCents = null,
    )

    private fun item(id: String, sessionId: String, total: Long) = ShoppingSessionItemEntity(
        id = id,
        sessionId = sessionId,
        shoppingListItemId = null,
        productId = null,
        name = null,
        barcode = null,
        quantity = "1",
        unit = MeasureUnit.UN,
        unitPriceCents = total,
        discountCents = 0,
        totalCents = total,
        priceSource = PriceSource.MANUAL,
        addedAt = 1,
    )
}

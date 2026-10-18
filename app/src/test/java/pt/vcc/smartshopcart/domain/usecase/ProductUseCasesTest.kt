package pt.vcc.smartshopcart.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
import pt.vcc.smartshopcart.data.repository.RoomShoppingListRepository
import pt.vcc.smartshopcart.data.repository.RoomShoppingSessionRepository
import pt.vcc.smartshopcart.data.repository.RoomStoreRepository
import pt.vcc.smartshopcart.domain.model.MeasureUnit
import pt.vcc.smartshopcart.domain.model.PriceSource
import pt.vcc.smartshopcart.domain.model.ProductSource
import pt.vcc.smartshopcart.domain.model.SessionMode
import pt.vcc.smartshopcart.domain.repository.RemoteProduct
import pt.vcc.smartshopcart.domain.repository.RemoteProductResult
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class ProductUseCasesTest {

    private lateinit var db: ShoppingDatabase
    private lateinit var products: RoomProductRepository
    private lateinit var sessions: RoomShoppingSessionRepository
    private lateinit var lists: RoomShoppingListRepository
    private lateinit var start: StartShoppingSessionUseCase
    private lateinit var complete: CompleteShoppingSessionUseCase
    private lateinit var addScanned: AddScannedItemUseCase
    private lateinit var resolve: ResolveProductUseCase
    private var clock = 1_000L
    private var nextId = 0
    private val time = TimeProvider { clock++ }
    private val ids = IdProvider { "id-${nextId++}" }

    private var online = true
    private var remoteResult: RemoteProductResult = RemoteProductResult.NotFound
    private val remoteCalls = mutableListOf<String>()

    private val nutella = "3017620422003"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ShoppingDatabase::class.java).allowMainThreadQueries().build()
        products = RoomProductRepository(db)
        sessions = RoomShoppingSessionRepository(db)
        lists = RoomShoppingListRepository(db)
        start = StartShoppingSessionUseCase(sessions, RoomStoreRepository(db, ids, time), ids, time)
        complete = CompleteShoppingSessionUseCase(sessions, ids, time)
        addScanned = AddScannedItemUseCase(sessions, products, lists, AddSessionItemUseCase(sessions, ids, time), ids, time)
        resolve = ResolveProductUseCase(
            products = products,
            remote = { barcode -> remoteCalls += barcode; remoteResult },
            network = { online },
            ids = ids,
            time = time,
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun startSession(store: String? = "Continente"): String =
        (start(SessionMode.CALCULATOR, storeName = store) as StartSessionResult.Started).session.id

    private fun scanned(barcode: String, name: String?, price: Long, suggested: Long? = null, listItemId: String? = null) =
        ScannedItemInput(barcode, null, name, BigDecimal.ONE, MeasureUnit.UN, price, suggested, listItemId)

    @Test
    fun `codigo invalido nao consulta nada`() = runTest {
        assertEquals(ResolveProductResult.InvalidBarcode, resolve("3017620422004", null))
        assertTrue(remoteCalls.isEmpty())
    }

    @Test
    fun `produto remoto e guardado e depois resolvido localmente sem rede`() = runTest {
        remoteResult = RemoteProductResult.Found(RemoteProduct(nutella, "Nutella", "Ferrero", "400 g", null, null))

        val first = resolve(nutella, null) as ResolveProductResult.Resolved
        assertEquals(ProductLookupStatus.REMOTE, first.status)
        assertEquals(ProductSource.OPEN_FOOD_FACTS, first.product!!.source)
        assertEquals("Ferrero", first.product!!.brand)

        online = false
        val second = resolve(nutella, null) as ResolveProductResult.Resolved
        assertEquals(ProductLookupStatus.LOCAL, second.status)
        assertEquals(first.product!!.id, second.product!!.id)
        assertEquals(listOf(nutella), remoteCalls)
    }

    @Test
    fun `sem rede, codigo interno e falhas nao bloqueiam`() = runTest {
        online = false
        assertEquals(ProductLookupStatus.OFFLINE, (resolve(nutella, null) as ResolveProductResult.Resolved).status)

        online = true
        assertEquals(ProductLookupStatus.INTERNAL_CODE, (resolve("2000000000008", null) as ResolveProductResult.Resolved).status)
        assertTrue(remoteCalls.isEmpty())

        remoteResult = RemoteProductResult.Failed
        assertEquals(ProductLookupStatus.REMOTE_ERROR, (resolve(nutella, null) as ResolveProductResult.Resolved).status)
        remoteResult = RemoteProductResult.Found(RemoteProduct(nutella, " ", null, null, null, null))
        val noName = resolve(nutella, null) as ResolveProductResult.Resolved
        assertEquals(ProductLookupStatus.NOT_FOUND, noName.status)
        assertNull(noName.product)
    }

    @Test
    fun `item lido cria produto manual e o preco seguinte e sugerido no mesmo supermercado`() = runTest {
        online = false
        val sessionId = startSession("Continente")
        val item = addScanned(sessionId, scanned("2000000000008", "Queijo fatiado", 249))
        assertEquals(PriceSource.MANUAL, item.priceSource)
        val product = products.findByBarcode("2000000000008")!!
        assertEquals(ProductSource.MANUAL, product.source)
        assertEquals(product.id, item.productId)
        complete(sessionId)

        val again = resolve("2000000000008", sessions.getSession(sessionId)!!.storeId) as ResolveProductResult.Resolved
        assertEquals(ProductLookupStatus.LOCAL, again.status)
        assertEquals(249L, again.suggestedPrice!!.priceCents)
        assertEquals("Continente", again.suggestedPrice!!.storeName)

        val otherStore = startSession("Pingo Doce")
        val elsewhere = resolve("2000000000008", sessions.getSession(otherStore)!!.storeId) as ResolveProductResult.Resolved
        assertNull(elsewhere.suggestedPrice)

        val accepted = addScanned(otherStore, scanned("2000000000008", "Queijo fatiado", 249, suggested = 249))
        assertEquals(PriceSource.HISTORY, accepted.priceSource)
        val changed = addScanned(otherStore, scanned("2000000000008", "Queijo fatiado", 259, suggested = 249))
        assertEquals(PriceSource.MANUAL, changed.priceSource)
    }

    @Test
    fun `item lido liga o item planeado ao produto`() = runTest {
        val list = CreateShoppingListUseCase(lists, ids, time)("Semana", null)
        val milk = SaveShoppingListItemUseCase(lists, ids, time)(list.id, null, ListItemInput("Leite", BigDecimal("6"), MeasureUnit.UN, null))
        val sessionId = (start(SessionMode.PLANNED, list.id) as StartSessionResult.Started).session.id

        val item = addScanned(sessionId, scanned("5601234567892", "Leite meio-gordo", 95, listItemId = milk.id))

        assertEquals(milk.id, item.shoppingListItemId)
        assertEquals(item.productId, lists.getItem(milk.id)!!.productId)
    }

    @Test
    fun `sessao inativa nao cria produto`() = runTest {
        val sessionId = startSession()
        complete(sessionId)
        try {
            addScanned(sessionId, scanned(nutella, "Nutella", 399))
            throw AssertionError("Devia falhar")
        } catch (_: SessionNotActiveException) {
        }
        assertNull(products.findByBarcode(nutella))
    }
}

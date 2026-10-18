package pt.vcc.smartshopcart.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pt.vcc.smartshopcart.core.common.IdProvider
import pt.vcc.smartshopcart.core.common.TimeProvider
import pt.vcc.smartshopcart.core.database.ShoppingDatabase
import pt.vcc.smartshopcart.data.repository.RoomShoppingListRepository
import pt.vcc.smartshopcart.data.repository.RoomShoppingSessionRepository
import pt.vcc.smartshopcart.data.repository.RoomStoreRepository
import pt.vcc.smartshopcart.domain.model.MeasureUnit
import pt.vcc.smartshopcart.domain.model.PlannedItemStatus
import pt.vcc.smartshopcart.domain.model.SessionMode
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class ShoppingListUseCasesTest {

    private lateinit var db: ShoppingDatabase
    private lateinit var lists: RoomShoppingListRepository
    private lateinit var sessions: RoomShoppingSessionRepository
    private var clock = 1_000L
    private var nextId = 0
    private val time = TimeProvider { clock++ }
    private val ids = IdProvider { "id-${nextId++}" }

    private lateinit var create: CreateShoppingListUseCase
    private lateinit var update: UpdateShoppingListUseCase
    private lateinit var archive: SetShoppingListArchivedUseCase
    private lateinit var duplicate: DuplicateShoppingListUseCase
    private lateinit var saveItem: SaveShoppingListItemUseCase
    private lateinit var startPlanned: StartPlannedSessionUseCase
    private lateinit var addItem: AddSessionItemUseCase
    private lateinit var removeItem: RemoveSessionItemUseCase
    private lateinit var skip: SetPlannedItemSkippedUseCase
    private val derive = DerivePlannedProgressUseCase()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ShoppingDatabase::class.java).allowMainThreadQueries().build()
        lists = RoomShoppingListRepository(db)
        sessions = RoomShoppingSessionRepository(db)
        val stores = RoomStoreRepository(db, ids, time)
        create = CreateShoppingListUseCase(lists, ids, time)
        update = UpdateShoppingListUseCase(lists, time)
        archive = SetShoppingListArchivedUseCase(lists, time)
        duplicate = DuplicateShoppingListUseCase(lists, ids, time)
        saveItem = SaveShoppingListItemUseCase(lists, ids, time)
        startPlanned = StartPlannedSessionUseCase(lists, StartShoppingSessionUseCase(sessions, stores, ids, time))
        addItem = AddSessionItemUseCase(sessions, ids, time)
        removeItem = RemoveSessionItemUseCase(sessions)
        skip = SetPlannedItemSkippedUseCase(sessions)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun listWithItems(): Pair<String, List<String>> {
        val list = create(" Semana ", 5000)
        val milk = saveItem(list.id, null, ListItemInput("Leite", BigDecimal("6"), MeasureUnit.UN, 89))
        val apples = saveItem(list.id, null, ListItemInput("Maçãs", BigDecimal("1.5"), MeasureUnit.KG, null))
        val bread = saveItem(list.id, null, ListItemInput("Pão", BigDecimal.ONE, MeasureUnit.UN, 120))
        return list.id to listOf(milk.id, apples.id, bread.id)
    }

    @Test
    fun `criar editar e arquivar listas`() = runTest {
        val (listId, itemIds) = listWithItems()
        val loaded = lists.observeList(listId).first()!!
        assertEquals("Semana", loaded.list.name)
        assertEquals(itemIds, loaded.items.map { it.id })
        assertEquals(listOf(0, 1, 2), loaded.items.map { it.sortOrder })

        update(listId, "Semana 2", null)
        assertEquals(null, lists.getList(listId)!!.budgetCents)

        archive(listId, true)
        assertTrue(lists.observeLists(archived = false).first().isEmpty())
        assertEquals(3, lists.observeLists(archived = true).first().single().itemCount)
    }

    @Test
    fun `duplicar copia itens com novos ids`() = runTest {
        val (listId, itemIds) = listWithItems()
        archive(listId, true)
        val copy = duplicate(listId, "Semana (cópia)")
        val loaded = lists.observeList(copy.id).first()!!
        assertEquals(false, loaded.list.archived)
        assertEquals(listOf("Leite", "Maçãs", "Pão"), loaded.items.map { it.name })
        assertTrue(loaded.items.none { it.id in itemIds })
        assertEquals(3, lists.observeList(listId).first()!!.items.size)
    }

    @Test
    fun `sessao planeada copia o orcamento da lista`() = runTest {
        val (listId, _) = listWithItems()
        val session = (startPlanned(listId, null, 5000) as StartSessionResult.Started).session
        assertEquals(SessionMode.PLANNED, session.mode)
        assertEquals(listId, session.shoppingListId)

        update(listId, "Semana", 9999)
        assertEquals(5000L, sessions.getSession(session.id)!!.budgetCents)
    }

    @Test
    fun `progresso com adicionados ignorados e extras`() = runTest {
        val (listId, itemIds) = listWithItems()
        val (milk, apples, bread) = itemIds
        val session = (startPlanned(listId, "Lidl", null) as StartSessionResult.Started).session

        addItem(session.id, NewSessionItem(unitPriceCents = 85, quantity = BigDecimal("6"), shoppingListItemId = milk, name = "Leite"))
        skip(session.id, apples, skipped = true)
        skip(session.id, bread, skipped = true)
        addItem(session.id, NewSessionItem(unitPriceCents = 299, name = "Chocolate"))

        suspend fun progress(): PlannedProgress {
            val details = sessions.observeSession(session.id).first()!!
            val listItems = lists.observeList(listId).first()!!.items
            return derive(listItems, details.items, sessions.observeSkippedListItems(session.id).first())
        }

        var p = progress()
        assertEquals(listOf(PlannedItemStatus.ADDED, PlannedItemStatus.SKIPPED, PlannedItemStatus.SKIPPED), p.entries.map { it.status })
        assertEquals(listOf("Chocolate"), p.extras.map { it.name })
        assertEquals(3, p.resolvedCount)

        // Adicionar um item ignorado limpa o estado SKIPPED.
        addItem(session.id, NewSessionItem(unitPriceCents = 115, shoppingListItemId = bread, name = "Pão"))
        skip(session.id, apples, skipped = false)
        p = progress()
        assertEquals(listOf(PlannedItemStatus.ADDED, PlannedItemStatus.PENDING, PlannedItemStatus.ADDED), p.entries.map { it.status })
        assertEquals(emptySet<String>(), sessions.observeSkippedListItems(session.id).first())

        // Remover o item de sessão devolve o item planeado a PENDING.
        removeItem(p.entries.first().sessionItems.single().id)
        assertEquals(PlannedItemStatus.PENDING, progress().entries.first().status)
        assertEquals(1, progress().extras.size)
    }
}

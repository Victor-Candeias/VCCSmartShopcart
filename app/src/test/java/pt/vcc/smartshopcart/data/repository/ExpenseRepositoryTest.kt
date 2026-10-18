package pt.vcc.smartshopcart.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pt.vcc.smartshopcart.core.common.IdProvider
import pt.vcc.smartshopcart.core.common.TimeProvider
import pt.vcc.smartshopcart.core.database.ShoppingDatabase
import pt.vcc.smartshopcart.domain.model.expense.ExpenseCategory
import pt.vcc.smartshopcart.domain.model.expense.ExpenseDraft
import pt.vcc.smartshopcart.domain.model.expense.ExpenseDraftItem
import pt.vcc.smartshopcart.domain.model.expense.ExpenseProduct

@RunWith(RobolectricTestRunner::class)
class ExpenseRepositoryTest {

    private lateinit var db: ShoppingDatabase
    private lateinit var repository: RoomExpenseRepository
    private var clock = 1_000L
    private var nextId = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ShoppingDatabase::class.java).allowMainThreadQueries().build()
        repository = RoomExpenseRepository(db, IdProvider { "id-${nextId++}" }, TimeProvider { clock++ })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seedCafe() = repository.importCatalog(
        listOf(ExpenseCategory("cafe", "Café e Pastelaria", "☕", 0, true)),
        listOf(
            ExpenseProduct("p-cafe", "cafe", "Café", null, 85, isFavorite = true, isActive = true, sortOrder = 0),
            ExpenseProduct("p-bola", "cafe", "Bola de Berlim", null, null, isFavorite = false, isActive = true, sortOrder = 1),
        ),
    )

    private fun draft(establishment: String?, vararg items: ExpenseDraftItem) =
        ExpenseDraft(establishmentName = establishment, occurredAt = 50_000, items = items.toList())

    @Test
    fun `grava cabecalho itens e total na mesma transacao`() = runTest {
        seedCafe()
        val id = repository.saveExpense(
            draft(
                "Café Central",
                ExpenseDraftItem("p-cafe", "Café", 1, 85),
                ExpenseDraftItem("p-bola", "Bola de Berlim", 1, 150),
            ),
        )

        val details = repository.getExpense(id)!!
        assertEquals(235L, details.expense.totalCents)
        assertEquals("Café Central", details.establishment?.name)
        assertEquals(listOf("Café", "Bola de Berlim"), details.items.map { it.nameSnapshot })

        val summary = repository.observeExpenses().first().single()
        assertEquals(2, summary.itemCount)
        assertEquals(setOf("cafe"), summary.categoryIds)
    }

    @Test
    fun `reutiliza estabelecimento sem distinguir maiusculas e permite guardar sem estabelecimento`() = runTest {
        repository.saveExpense(draft("Café Central", ExpenseDraftItem(null, "Café", 1, 85)))
        repository.saveExpense(draft("café central", ExpenseDraftItem(null, "Café", 1, 90)))
        val withoutPlace = repository.saveExpense(draft("  ", ExpenseDraftItem(null, "Água", 1, 100)))

        assertEquals(1, repository.observeEstablishments().first().size)
        assertNull(repository.getExpense(withoutPlace)!!.expense.establishmentId)
    }

    @Test
    fun `editar substitui itens mantem createdAt e recalcula total`() = runTest {
        val id = repository.saveExpense(draft(null, ExpenseDraftItem(null, "Café", 1, 85)))
        val createdAt = repository.getExpense(id)!!.expense.createdAt

        repository.saveExpense(draft("Pastelaria", ExpenseDraftItem(null, "Café", 2, 80)), expenseId = id)

        val details = repository.getExpense(id)!!
        assertEquals(createdAt, details.expense.createdAt)
        assertTrue(details.expense.updatedAt > createdAt)
        assertEquals(160L, details.expense.totalCents)
        assertEquals(1, details.items.size)
    }

    @Test
    fun `eliminar despesa remove itens em cascata`() = runTest {
        val id = repository.saveExpense(draft(null, ExpenseDraftItem(null, "Café", 1, 85)))

        repository.deleteExpense(id)

        assertNull(repository.getExpense(id))
        assertTrue(db.expenseDao().getItems(id).isEmpty())
    }

    @Test
    fun `alterar o catalogo nao altera despesas antigas`() = runTest {
        seedCafe()
        val id = repository.saveExpense(draft(null, ExpenseDraftItem("p-cafe", "Café", 1, 85)))

        repository.upsertProduct(repository.getProduct("p-cafe")!!.copy(name = "Expresso", defaultPriceCents = 95))

        val item = repository.getExpense(id)!!.items.single()
        assertEquals("Café", item.nameSnapshot)
        assertEquals(85L, item.unitPriceCents)
    }

    @Test
    fun `importacao ignora ids existentes e nao sobrescreve alteracoes`() = runTest {
        seedCafe()
        repository.upsertProduct(repository.getProduct("p-cafe")!!.copy(name = "Bica", isActive = false))

        seedCafe()

        val all = repository.observeProducts(null, includeInactive = true).first()
        assertEquals(2, all.size)
        assertEquals("Bica", all.first { it.id == "p-cafe" }.name)
        assertEquals(listOf("p-bola"), repository.observeProducts("cafe").first().map { it.id })
    }

    @Test
    fun `reordenar grava a nova ordem`() = runTest {
        seedCafe()

        repository.reorderProducts(listOf("p-bola", "p-cafe"))

        assertEquals(listOf("p-bola", "p-cafe"), repository.observeProducts("cafe").first().map { it.id })
    }

    @Test
    fun `rejeita valores invalidos sem gravar nada`() = runTest {
        val invalid = listOf(
            draft(null),
            draft(null, ExpenseDraftItem(null, "Café", 0, 85)),
            draft(null, ExpenseDraftItem(null, "Café", 1, -1)),
            draft(null, ExpenseDraftItem(null, " ", 1, 85)),
        )
        invalid.forEach { candidate ->
            try {
                repository.saveExpense(candidate)
                fail("Devia rejeitar $candidate")
            } catch (expected: IllegalArgumentException) {
                // esperado
            }
        }
        assertTrue(repository.observeExpenses().first().isEmpty())
    }
}

package pt.vcc.smartshopcart.domain.usecase.expense

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pt.vcc.smartshopcart.core.common.IdProvider
import pt.vcc.smartshopcart.core.common.TimeProvider
import pt.vcc.smartshopcart.core.database.ShoppingDatabase
import pt.vcc.smartshopcart.data.repository.RoomExpenseRepository
import pt.vcc.smartshopcart.domain.model.expense.ExpenseCategory
import pt.vcc.smartshopcart.domain.model.expense.ExpenseProduct

@RunWith(RobolectricTestRunner::class)
class ExpenseCatalogUseCasesTest {

    private lateinit var db: ShoppingDatabase
    private lateinit var repository: RoomExpenseRepository
    private var nextId = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ShoppingDatabase::class.java).allowMainThreadQueries().build()
        repository = RoomExpenseRepository(db, IdProvider { "id-${nextId++}" }, TimeProvider { 1_000L })
    }

    @After
    fun tearDown() = db.close()

    private fun product(id: String, category: String, name: String, order: Int, favorite: Boolean = false, active: Boolean = true) =
        ExpenseProduct(id, category, name, null, null, favorite, active, order)

    private suspend fun seed() = repository.importCatalog(
        listOf(ExpenseCategory("cafe", "Café", null, 0, true), ExpenseCategory("transp", "Transportes", null, 1, true)),
        listOf(
            product("galao", "cafe", "Galão", 0),
            product("nata", "cafe", "Pastel de nata", 1, favorite = true),
            product("agua", "cafe", "Água", 2, active = false),
            product("bola", "cafe", "Bola de Berlim", 3),
            product("taxi", "transp", "Táxi", 0),
        ),
    )

    @Test
    fun `pesquisa ignora acentos e maiusculas e pode por favoritos primeiro`() {
        val products = listOf(product("galao", "cafe", "Galão", 0), product("taxi", "t", "Táxi", 0), product("nata", "cafe", "Nata", 1, favorite = true))

        assertEquals(listOf("galao"), ExpenseSearch.filter(products, null, "GALAO").map { it.id })
        assertEquals(listOf("taxi"), ExpenseSearch.filter(products, null, "taxi").map { it.id })
        assertEquals(listOf("nata", "galao"), ExpenseSearch.filter(products, "cafe", "", favoritesFirst = true).map { it.id })
    }

    @Test
    fun `catalogo ordena por categoria e ordem e esconde ocultos`() = runTest {
        seed()
        val catalog = GetExpenseCatalogUseCase(repository)

        assertEquals(listOf("galao", "nata", "bola", "taxi"), catalog().first().products.map { it.id })
        assertEquals(listOf("galao", "nata", "agua", "bola", "taxi"), catalog(includeHidden = true).first().products.map { it.id })
    }

    @Test
    fun `cria e edita produto preservando favorito e validando dados`() = runTest {
        seed()
        val save = SaveExpenseProductUseCase(repository) { "novo" }

        val created = save(null, "cafe", "  Torrada ", "🍞", 150)
        assertEquals("Torrada", created.name)
        assertEquals(4, created.sortOrder)

        val edited = save("nata", "transp", "Nata", null, null)
        assertTrue(edited.isFavorite)
        assertEquals(1, edited.sortOrder)

        assertTrue(runCatching { save(null, "cafe", " ", null, null) }.isFailure)
        assertTrue(runCatching { save(null, "cafe", "X", null, -1) }.isFailure)
    }

    @Test
    fun `move salta ocultos e mantem a posicao deles`() = runTest {
        seed()
        val move = MoveExpenseProductUseCase(repository)

        assertTrue(move("bola", up = true))
        assertEquals(listOf("galao", "bola", "agua", "nata"), repository.observeProducts("cafe", true).first().map { it.id })
        assertFalse(move("galao", up = true))
        assertTrue(move("galao", up = false))
        assertEquals(listOf("bola", "galao", "agua", "nata"), repository.observeProducts("cafe", true).first().map { it.id })
    }

    @Test
    fun `favorito e ocultar alteram apenas o indicador`() = runTest {
        seed()
        SetExpenseProductFavoriteUseCase(repository)("galao", true)
        SetExpenseProductActiveUseCase(repository)("taxi", false)

        assertTrue(repository.getProduct("galao")!!.isFavorite)
        assertFalse(repository.getProduct("taxi")!!.isActive)
        assertEquals(listOf("galao", "nata", "bola"), GetExpenseCatalogUseCase(repository)().first().products.map { it.id })
    }
}

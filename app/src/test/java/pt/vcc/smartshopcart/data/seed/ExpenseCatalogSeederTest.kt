package pt.vcc.smartshopcart.data.seed

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
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
import pt.vcc.smartshopcart.domain.model.expense.ExpenseProduct
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ExpenseCatalogSeederTest {

    private lateinit var db: ShoppingDatabase
    private lateinit var repository: RoomExpenseRepository
    private var json = seedJson(version = 1, extraProduct = false)
    private val versions = object : ExpenseSeedVersionStore {
        var stored = 0
        override suspend fun importedVersion() = stored
        override suspend fun setImportedVersion(version: Int) {
            stored = version
        }
    }
    private lateinit var seeder: ExpenseCatalogSeeder

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ShoppingDatabase::class.java).allowMainThreadQueries().build()
        var nextId = 0
        repository = RoomExpenseRepository(db, IdProvider { "id-${nextId++}" }, TimeProvider { 1_000L })
        seeder = ExpenseCatalogSeeder({ json }, versions, repository, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun products() = repository.observeProducts(null, includeInactive = true).first()

    @Test
    fun `importa o seed uma vez e regista a versao`() = runTest {
        assertTrue(seeder.seedIfNeeded())
        assertFalse(seeder.seedIfNeeded())

        assertEquals(1, versions.stored)
        assertEquals(listOf("Café e Pastelaria", "Transportes"), repository.observeCategories().first().map { it.name })
        val cafe = products().filter { it.categoryId == "seed-cat-cafe" }
        assertEquals(listOf("seed-cafe" to 0, "seed-nata" to 1), cafe.map { it.id to it.sortOrder })
        assertEquals(3, products().size)
    }

    @Test
    fun `reimportar nao duplica nem sobrescreve alteracoes do utilizador`() = runTest {
        seeder.seedIfNeeded()
        val cafe = repository.getProduct("seed-cafe")!!
        repository.upsertProduct(cafe.copy(name = "Bica", defaultPriceCents = 90, isFavorite = false))
        repository.upsertProduct(repository.getProduct("seed-nata")!!.copy(isActive = false))
        repository.reorderProducts(listOf("seed-nata", "seed-cafe"))
        repository.upsertProduct(ExpenseProduct("custom", "seed-cat-cafe", "Torrada", null, 150, false, true, 2))

        json = seedJson(version = 2, extraProduct = true)
        assertTrue(seeder.seedIfNeeded())

        val byId = products().associateBy { it.id }
        assertEquals(5, byId.size)
        assertEquals("Bica", byId.getValue("seed-cafe").name)
        assertEquals(90L, byId.getValue("seed-cafe").defaultPriceCents)
        assertFalse(byId.getValue("seed-cafe").isFavorite)
        assertEquals(1, byId.getValue("seed-cafe").sortOrder)
        assertFalse(byId.getValue("seed-nata").isActive)
        assertEquals(0, byId.getValue("seed-nata").sortOrder)
        assertEquals("Torrada", byId.getValue("custom").name)
        assertEquals("Água", byId.getValue("seed-agua").name)
        assertEquals(2, versions.stored)
    }

    @Test
    fun `seed da aplicacao e valido e com IDs estaveis`() {
        val seed = ExpenseCatalogSeed.parse(File("src/main/assets/expense_catalog.json").readText())

        assertEquals(
            listOf("Café e Pastelaria", "Restauração", "Transportes", "Outras despesas"),
            seed.categories.map { it.name },
        )
        assertTrue((seed.categories.map { it.id } + seed.products.map { it.id }).all { it.startsWith("seed-") })
        assertTrue(seed.products.any { it.name == "Pastel de nata" })
        assertTrue(seed.products.any { it.name == "Portagem" })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `seed com produtos duplicados e rejeitado`() {
        ExpenseCatalogSeed.parse(
            """
            {"version":1,"categories":[{"id":"c","name":"C"}],
             "products":[{"id":"p","categoryId":"c","name":"A"},{"id":"p","categoryId":"c","name":"B"}]}
            """,
        )
    }

    private fun seedJson(version: Int, extraProduct: Boolean): String {
        val extra = if (extraProduct) """,{"id":"seed-agua","categoryId":"seed-cat-cafe","name":"Água"}""" else ""
        return """
            {"version":$version,
             "categories":[{"id":"seed-cat-cafe","name":"Café e Pastelaria","icon":"☕"},
                           {"id":"seed-cat-transportes","name":"Transportes"}],
             "products":[{"id":"seed-cafe","categoryId":"seed-cat-cafe","name":"Café","favorite":true},
                         {"id":"seed-nata","categoryId":"seed-cat-cafe","name":"Pastel de nata"},
                         {"id":"seed-taxi","categoryId":"seed-cat-transportes","name":"Táxi"}$extra]}
        """
    }
}

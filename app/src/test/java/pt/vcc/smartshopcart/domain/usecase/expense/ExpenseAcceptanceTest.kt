package pt.vcc.smartshopcart.domain.usecase.expense

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pt.vcc.smartshopcart.core.common.IdProvider
import pt.vcc.smartshopcart.core.common.TimeProvider
import pt.vcc.smartshopcart.core.database.ShoppingDatabase
import pt.vcc.smartshopcart.core.datastore.ExpensePreferences
import pt.vcc.smartshopcart.core.money.MoneyFormatter
import pt.vcc.smartshopcart.core.money.MoneyParser
import pt.vcc.smartshopcart.data.repository.RoomExpenseRepository
import pt.vcc.smartshopcart.domain.model.expense.ExpenseCategory
import pt.vcc.smartshopcart.domain.model.expense.ExpenseDraft
import pt.vcc.smartshopcart.domain.model.expense.ExpenseDraftItem
import pt.vcc.smartshopcart.domain.model.expense.ExpenseEditorDraft
import pt.vcc.smartshopcart.domain.model.expense.ExpenseProduct
import pt.vcc.smartshopcart.domain.model.expense.PriceSuggestionSource

/**
 * Critérios de aceitação do §11 da especificação "Despesas Rápidas", exercitados sobre Room e
 * DataStore reais (tudo local: nenhuma dependência de rede).
 */
@RunWith(RobolectricTestRunner::class)
class ExpenseAcceptanceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: ShoppingDatabase
    private lateinit var repository: RoomExpenseRepository
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var drafts: ExpensePreferences
    private var nextId = 0
    private val ids = IdProvider { "id-${nextId++}" }
    private var now = 10_000L
    private val time = TimeProvider { now }

    private val cafe = ExpenseProduct("cafe", "cat-cafe", "Café", "☕", 80, true, true, 0)
    private val bola = ExpenseProduct("bola", "cat-cafe", "Bola de Berlim", null, 140, false, true, 1)

    @Before
    fun setUp() = runTest {
        db = openDatabase()
        repository = RoomExpenseRepository(db, ids, time)
        repository.importCatalog(listOf(ExpenseCategory("cat-cafe", "Café e Pastelaria", "☕", 0, true)), listOf(cafe, bola))
        dataStoreScope = CoroutineScope(Dispatchers.IO + Job())
        drafts = ExpensePreferences(
            PreferenceDataStoreFactory.create(scope = dataStoreScope) { tmp.newFile("expense.preferences_pb") },
        )
    }

    @After
    fun tearDown() {
        db.close()
        dataStoreScope.cancel()
        context.deleteDatabase(DB_NAME)
    }

    private fun openDatabase() =
        Room.databaseBuilder(context, ShoppingDatabase::class.java, DB_NAME).allowMainThreadQueries().build()

    private fun save() = SaveExpenseUseCase(repository, drafts)

    private fun cents(text: String) = MoneyParser.parseToCents(text)!!

    @Test
    fun `cafe mais bola de Berlim com precos diferentes soma 2,35 euros e guarda sem estabelecimento`() = runTest {
        val draft = ExpenseEditorDraft(occurredAt = now)
            .addProduct(cafe, "l1", null)
            .addProduct(bola, "l2", null)
            .setPrice("l1", cents("0,85"))
            .setPrice("l2", cents("1,50"))

        assertEquals(235L, draft.totalCents)
        assertEquals("2,35\u00A0€", MoneyFormatter.format(draft.totalCents))

        val saved = save()(draft) as SaveExpenseResult.Saved
        val stored = repository.getExpense(saved.expenseId)!!
        assertNull(stored.establishment)
        assertEquals(listOf(85L, 150L), stored.items.map { it.unitPriceCents })
        assertEquals(235L, stored.expense.totalCents)
    }

    @Test
    fun `alterar quantidade atualiza o total imediatamente`() {
        val draft = ExpenseEditorDraft(occurredAt = now).addProduct(cafe, "l1", 85)
        assertEquals(85L, draft.totalCents)
        assertEquals(255L, draft.changeQuantity("l1", +2).totalCents)
        assertEquals(170L, draft.changeQuantity("l1", +2).changeQuantity("l1", -1).totalCents)
    }

    @Test
    fun `criar produto personalizado nao perde o rascunho`() = runTest {
        val draft = ExpenseEditorDraft(occurredAt = now, establishmentName = "Pastelaria").addProduct(cafe, "l1", 85)
        drafts.save(draft)

        val torrada = SaveExpenseProductUseCase(repository, ids)(null, "cat-cafe", "Torrada", null, 120)
        val updated = drafts.get()!!.addProduct(torrada, "l2", torrada.defaultPriceCents, PriceSuggestionSource.CATALOG)
        drafts.save(updated)

        val restored = drafts.get()!!
        assertEquals(listOf("Café", "Torrada"), restored.lines.map { it.name })
        assertEquals("Pastelaria", restored.establishmentName)
        assertEquals(PriceSuggestionSource.CATALOG, restored.lines[1].priceSource)
    }

    @Test
    fun `editar preco habitual do catalogo nao altera despesas anteriores`() = runTest {
        val saved = save()(ExpenseEditorDraft(occurredAt = now).addProduct(cafe, "l1", 80)) as SaveExpenseResult.Saved

        SaveExpenseProductUseCase(repository, ids)("cafe", "cat-cafe", "Café expresso", "☕", 95)

        val stored = repository.getExpense(saved.expenseId)!!
        assertEquals("Café", stored.items.single().nameSnapshot)
        assertEquals(80L, stored.items.single().unitPriceCents)
        assertEquals(80L, stored.expense.totalCents)
    }

    @Test
    fun `repetir despesa sugere precos que podem ser corrigidos`() = runTest {
        val original = ExpenseEditorDraft(occurredAt = now, establishmentName = "Pastelaria Central")
            .addProduct(cafe, "l1", 85).addProduct(bola, "l2", 150)
        val first = save()(original) as SaveExpenseResult.Saved

        now = 20_000L
        val suggest = SuggestProductPriceUseCase(repository)
        val repeated = RepeatExpenseUseCase(repository, suggest, time, ids)(first.expenseId)!!
        assertEquals(20_000L, repeated.occurredAt)
        assertEquals(listOf(85L, 150L), repeated.lines.map { it.unitPriceCents })
        assertTrue(repeated.lines.all { it.priceSource == PriceSuggestionSource.SAME_ESTABLISHMENT })

        val corrected = repeated.setPrice(repeated.lines[1].key, 160)
        assertNull(corrected.lines[1].priceSource)
        val second = save()(corrected) as SaveExpenseResult.Saved

        assertEquals(245L, second.totalCents)
        assertEquals(235L, repository.getExpense(first.expenseId)!!.expense.totalCents)
        assertEquals(160L, suggest("bola", "Pastelaria Central").cents)
    }

    @Test
    fun `historico e rascunho sobrevivem a reinicio da aplicacao`() = runTest {
        save()(ExpenseEditorDraft(occurredAt = now).addProduct(cafe, "l1", 80))
        drafts.save(ExpenseEditorDraft(occurredAt = now).addProduct(bola, "l2", 140))
        db.close()

        db = openDatabase()
        repository = RoomExpenseRepository(db, ids, time)

        assertEquals(1, repository.observeExpenses().first().size)
        assertNotNull(drafts.get())
        assertEquals("Bola de Berlim", drafts.get()!!.lines.single().name)
    }

    @Test
    fun `valores invalidos nao sao guardados`() = runTest {
        val noPrice = ExpenseEditorDraft(occurredAt = now).addProduct(cafe, "l1", null)
        assertEquals(SaveExpenseResult.Invalid(ExpenseValidation.MissingPrice), save()(noPrice))
        assertEquals(SaveExpenseResult.Invalid(ExpenseValidation.NoItems), save()(ExpenseEditorDraft(occurredAt = now)))
        assertTrue(runCatching { noPrice.setPrice("l1", -1) }.isFailure)
        assertNull(MoneyParser.parseToCents("-1,00"))
        assertTrue(
            runCatching {
                repository.saveExpense(ExpenseDraft(null, now, listOf(ExpenseDraftItem("cafe", "Café", 0, 80))))
            }.isFailure,
        )
        assertTrue(repository.observeExpenses().first().isEmpty())
    }

    private companion object {
        const val DB_NAME = "expense-acceptance.db"
    }
}

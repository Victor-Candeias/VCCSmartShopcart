package pt.vcc.smartshopcart.domain.usecase.expense

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import pt.vcc.smartshopcart.data.repository.RoomExpenseRepository
import pt.vcc.smartshopcart.domain.model.expense.ExpenseCategory
import pt.vcc.smartshopcart.domain.model.expense.ExpenseEditorDraft
import pt.vcc.smartshopcart.domain.model.expense.ExpenseProduct
import pt.vcc.smartshopcart.domain.repository.ExpenseDraftStore
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class ExpenseUseCasesTest {

    private lateinit var db: ShoppingDatabase
    private lateinit var repository: RoomExpenseRepository
    private val drafts = FakeDraftStore()
    private var nextId = 0

    private val cafe = ExpenseProduct("cafe-expresso", "cafe", "Café", null, 80, true, true, 0)
    private val nata = ExpenseProduct("nata", "cafe", "Pastel de nata", null, null, false, true, 1)

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ShoppingDatabase::class.java).allowMainThreadQueries().build()
        repository = RoomExpenseRepository(db, IdProvider { "id-${nextId++}" }, TimeProvider { 1_000L })
        repository.importCatalog(listOf(ExpenseCategory("cafe", "Café", null, 0, true)), listOf(cafe, nata))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `tocar duas vezes no mesmo produto soma quantidade e calcula total`() {
        val draft = ExpenseEditorDraft(occurredAt = 0)
            .addProduct(cafe, "l1", 80)
            .addProduct(cafe, "l2", 80)
            .addProduct(nata, "l3", null)

        assertEquals(2, draft.lines.size)
        assertEquals(2, draft.lines[0].quantity)
        assertEquals(160, draft.totalCents)
        assertEquals(1, draft.missingPriceCount)

        val priced = draft.setPrice("l3", 130).changeQuantity("l1", -5)
        assertEquals(1, priced.lines[0].quantity)
        assertEquals(210, priced.totalCents)
        assertTrue(runCatching { priced.setPrice("l3", -1) }.isFailure)
        assertEquals(listOf("l3"), priced.removeLine("l1").lines.map { it.key })
    }

    @Test
    fun `validacao exige itens e precos definidos`() {
        val empty = ExpenseEditorDraft(occurredAt = 0, establishmentName = "Pastelaria")
        assertEquals(ExpenseValidation.NoItems, ExpenseValidation.validate(empty))

        val withoutPrice = empty.addProduct(nata, "l1", null)
        assertEquals(ExpenseValidation.MissingPrice, ExpenseValidation.validate(withoutPrice))

        val valid = ExpenseValidation.validate(withoutPrice.setPrice("l1", 0).copy(establishmentName = "  ", notes = " x "))
        valid as ExpenseValidation.Valid
        assertNull(valid.draft.establishmentName)
        assertEquals("x", valid.draft.notes)
        assertEquals(0, valid.draft.totalCents)
    }

    @Test
    fun `guardar grava despesa e limpa o rascunho`() = runTest {
        val save = SaveExpenseUseCase(repository, drafts)
        val draft = ExpenseEditorDraft(occurredAt = 5_000, establishmentName = "Pastelaria Central")
            .addProduct(cafe, "l1", 80)
            .addProduct(cafe, "l1b", 80)
            .addProduct(nata, "l2", 130)
        drafts.save(draft)

        val result = save(draft)

        result as SaveExpenseResult.Saved
        assertEquals(290, result.totalCents)
        assertNull(drafts.get())
        val stored = repository.getExpense(result.expenseId)!!
        assertEquals(290, stored.expense.totalCents)
        assertEquals("Pastelaria Central", stored.establishment?.name)
        assertEquals(listOf("Café" to 2, "Pastel de nata" to 1), stored.items.map { it.nameSnapshot to it.quantity })
    }

    @Test
    fun `guardar invalido nao grava nem limpa o rascunho`() = runTest {
        val save = SaveExpenseUseCase(repository, drafts)
        val draft = ExpenseEditorDraft(occurredAt = 5_000).addProduct(nata, "l1", null)
        drafts.save(draft)

        val result = save(draft)

        assertEquals(SaveExpenseResult.Invalid(ExpenseValidation.MissingPrice), result)
        assertNotNull(drafts.get())
        assertTrue(repository.observeExpenses().first().isEmpty())
    }

    @Test
    fun `editar atualiza a mesma despesa`() = runTest {
        val save = SaveExpenseUseCase(repository, drafts)
        val created = save(ExpenseEditorDraft(occurredAt = 5_000).addProduct(cafe, "l1", 80)) as SaveExpenseResult.Saved

        val edited = save(
            ExpenseEditorDraft(editingExpenseId = created.expenseId, occurredAt = 5_000).addProduct(nata, "l2", 150),
        ) as SaveExpenseResult.Saved

        assertEquals(created.expenseId, edited.expenseId)
        assertEquals(1, repository.observeExpenses().first().size)
        assertEquals(listOf("Pastel de nata"), repository.getExpense(created.expenseId)!!.items.map { it.nameSnapshot })
    }

    @Test
    fun `nova despesa a partir de produto usa preco habitual e hora atual`() = runTest {
        val start = StartExpenseDraftUseCase(repository, SuggestProductPriceUseCase(repository), TimeProvider { 42L }, IdProvider { "k" })

        val fromProduct = start(productId = "cafe-expresso")
        assertEquals(42L, fromProduct.occurredAt)
        assertEquals("cafe", fromProduct.selectedCategoryId)
        assertEquals(80L, fromProduct.lines.single().unitPriceCents)

        val fromCategory = start(categoryId = "cafe")
        assertTrue(fromCategory.lines.isEmpty())
        assertEquals("cafe", fromCategory.selectedCategoryId)
    }

    @Test
    fun `editar carrega a despesa mantendo data e repetir usa a data atual`() = runTest {
        val save = SaveExpenseUseCase(repository, drafts)
        val original = ExpenseEditorDraft(occurredAt = 5_000, establishmentName = "Café Central", notes = "almoço")
            .addProduct(cafe, "l1", 80).changeQuantity("l1", 1).addProduct(nata, "l2", 130)
        val saved = save(original) as SaveExpenseResult.Saved

        val edit = LoadExpenseDraftUseCase(repository) { "k${nextId++}" }(saved.expenseId)!!
        assertEquals(saved.expenseId, edit.editingExpenseId)
        assertEquals(5_000L, edit.occurredAt)
        assertEquals("Café Central", edit.establishmentName)
        assertEquals("almoço", edit.notes)
        assertEquals("cafe", edit.selectedCategoryId)
        assertEquals(listOf(Triple("Café", 2, 80L), Triple("Pastel de nata", 1, 130L)), edit.lines.map { Triple(it.name, it.quantity, it.unitPriceCents) })

        val repeated = RepeatExpenseUseCase(repository, SuggestProductPriceUseCase(repository), TimeProvider { 9_000L }) { "r${nextId++}" }(saved.expenseId)!!
        assertNull(repeated.editingExpenseId)
        assertEquals(9_000L, repeated.occurredAt)
        assertEquals(edit.lines.map { it.name to it.quantity }, repeated.lines.map { it.name to it.quantity })

        val second = save(repeated) as SaveExpenseResult.Saved
        assertTrue(second.expenseId != saved.expenseId)
        assertEquals(2, repository.observeExpenses().first().size)

        assertNull(LoadExpenseDraftUseCase(repository) { "x" }("inexistente"))
    }

    @Test
    fun `eliminar remove a despesa e filtros do historico`() = runTest {
        val save = SaveExpenseUseCase(repository, drafts)
        val a = save(ExpenseEditorDraft(occurredAt = 1, establishmentName = "Pastelaria").addProduct(cafe, "a", 80)) as SaveExpenseResult.Saved
        save(ExpenseEditorDraft(occurredAt = 2, establishmentName = "Bar").addProduct(nata, "b", 130))
        save(ExpenseEditorDraft(occurredAt = 3).copy(lines = listOf(pt.vcc.smartshopcart.domain.model.expense.ExpenseEditorLine("c", null, "Livre", 1, 50))))

        val all = repository.observeExpenses().first()
        assertEquals(listOf("Bar", "Pastelaria"), ExpenseHistoryFilter.establishments(all))
        assertEquals(2, ExpenseHistoryFilter.apply(all, "cafe", null).size)
        assertEquals(1, ExpenseHistoryFilter.apply(all, "cafe", "pastelaria").size)
        assertEquals(3, ExpenseHistoryFilter.apply(all, null, null).size)

        DeleteExpenseUseCase(repository)(a.expenseId)
        assertNull(repository.getExpense(a.expenseId))
        assertEquals(2, repository.observeExpenses().first().size)
    }

    @Test
    fun `alterar data mantem a hora e alterar hora mantem a data`() {
        val zone = ZoneId.of("Europe/Lisbon")
        val occurredAt = LocalDateTime.of(2026, 7, 10, 9, 30).atZone(zone).toInstant().toEpochMilli()
        val pickedDate = LocalDateTime.of(2026, 1, 3, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

        val withDate = ExpenseDateTime.withDate(occurredAt, pickedDate, zone)
        assertEquals(LocalDateTime.of(2026, 1, 3, 9, 30), LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(withDate), zone))
        assertEquals(pickedDate, ExpenseDateTime.utcDateMillis(withDate, zone))

        val withTime = ExpenseDateTime.withTime(occurredAt, 21, 5, zone)
        assertEquals(LocalDateTime.of(2026, 7, 10, 21, 5), LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(withTime), zone))
    }

    private class FakeDraftStore : ExpenseDraftStore {
        private val state = MutableStateFlow<ExpenseEditorDraft?>(null)
        override val draft: Flow<ExpenseEditorDraft?> = state
        override suspend fun get(): ExpenseEditorDraft? = state.value
        override suspend fun save(draft: ExpenseEditorDraft) { state.value = draft }
        override suspend fun clear() { state.value = null }
    }
}

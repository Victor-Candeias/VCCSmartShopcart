package pt.vcc.smartshopcart.domain.usecase.expense

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pt.vcc.smartshopcart.core.common.IdProvider
import pt.vcc.smartshopcart.core.common.TimeProvider
import pt.vcc.smartshopcart.core.database.ShoppingDatabase
import pt.vcc.smartshopcart.data.repository.RoomExpenseRepository
import pt.vcc.smartshopcart.domain.model.expense.ExpenseCategory
import pt.vcc.smartshopcart.domain.model.expense.ExpenseDraft
import pt.vcc.smartshopcart.domain.model.expense.ExpenseDraftItem
import pt.vcc.smartshopcart.domain.model.expense.ExpenseEditorDraft
import pt.vcc.smartshopcart.domain.model.expense.ExpenseProduct
import pt.vcc.smartshopcart.domain.model.expense.PriceSuggestion
import pt.vcc.smartshopcart.domain.model.expense.PriceSuggestionSource.CATALOG
import pt.vcc.smartshopcart.domain.model.expense.PriceSuggestionSource.LAST_PAID
import pt.vcc.smartshopcart.domain.model.expense.PriceSuggestionSource.SAME_ESTABLISHMENT

@RunWith(RobolectricTestRunner::class)
class SuggestProductPriceUseCaseTest {

    private lateinit var db: ShoppingDatabase
    private lateinit var repository: RoomExpenseRepository
    private lateinit var suggest: SuggestProductPriceUseCase
    private var nextId = 0

    private val cafe = ExpenseProduct("cafe", "c", "Café", null, 70, false, true, 0)
    private val nata = ExpenseProduct("nata", "c", "Nata", null, null, false, true, 1)

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ShoppingDatabase::class.java).allowMainThreadQueries().build()
        repository = RoomExpenseRepository(db, IdProvider { "id-${nextId++}" }, TimeProvider { 1_000L })
        repository.importCatalog(listOf(ExpenseCategory("c", "Café", null, 0, true)), listOf(cafe, nata))
        suggest = SuggestProductPriceUseCase(repository)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun pay(place: String?, at: Long, productId: String, cents: Long) {
        repository.saveExpense(ExpenseDraft(place, at, listOf(ExpenseDraftItem(productId, productId, 1, cents))))
    }

    @Test
    fun `sem historico usa preco habitual e depois vazio`() = runTest {
        assertEquals(PriceSuggestion(70, CATALOG), suggest("cafe", "Pastelaria"))
        assertEquals(PriceSuggestion.NONE, suggest("nata", "Pastelaria"))
        assertEquals(PriceSuggestion(55, LAST_PAID), suggest(null, null, fallbackCents = 55))
    }

    @Test
    fun `prioridade mesmo estabelecimento depois ultimo pago pela data da despesa`() = runTest {
        pay("Pastelaria", at = 100, productId = "cafe", cents = 80)
        pay("Aeroporto", at = 300, productId = "cafe", cents = 150)
        pay("Pastelaria", at = 50, productId = "cafe", cents = 60)

        assertEquals(PriceSuggestion(80, SAME_ESTABLISHMENT), suggest("cafe", "pastelaria "))
        assertEquals(PriceSuggestion(150, LAST_PAID), suggest("cafe", "Outro café"))
        assertEquals(PriceSuggestion(150, LAST_PAID), suggest("cafe", " "))
    }

    @Test
    fun `mudar estabelecimento so recalcula linhas ainda sugeridas`() = runTest {
        pay("Pastelaria", at = 100, productId = "cafe", cents = 80)
        pay("Pastelaria", at = 100, productId = "nata", cents = 130)
        pay("Bar", at = 200, productId = "cafe", cents = 100)
        pay("Bar", at = 200, productId = "nata", cents = 150)
        val draft = ExpenseEditorDraft(occurredAt = 0)
            .addProduct(cafe, "l1", 100, LAST_PAID)
            .addProduct(nata, "l2", 150, LAST_PAID)
            .setPrice("l2", 140)
            .copy(establishmentName = "Pastelaria")

        val updated = draft.applySuggestions(suggest.suggestionsFor(draft))

        assertEquals(80L, updated.lines[0].unitPriceCents)
        assertEquals(SAME_ESTABLISHMENT, updated.lines[0].priceSource)
        assertEquals(140L, updated.lines[1].unitPriceCents)
        assertNull(updated.lines[1].priceSource)
    }

    @Test
    fun `repetir sugere precos com recurso ao preco original`() = runTest {
        val id = repository.saveExpense(
            ExpenseDraft(
                "Bar",
                100,
                listOf(ExpenseDraftItem("cafe", "Café", 2, 90), ExpenseDraftItem(null, "Gorjeta", 1, 50)),
            ),
        )
        pay("Bar", at = 500, productId = "cafe", cents = 95)

        val repeated = RepeatExpenseUseCase(repository, suggest, TimeProvider { 999 }) { "k${nextId++}" }(id)!!

        assertEquals(999L, repeated.occurredAt)
        assertEquals(listOf(95L, 50L), repeated.lines.map { it.unitPriceCents })
        assertEquals(listOf(SAME_ESTABLISHMENT, LAST_PAID), repeated.lines.map { it.priceSource })
        assertEquals(2, repeated.lines[0].quantity)
    }
}

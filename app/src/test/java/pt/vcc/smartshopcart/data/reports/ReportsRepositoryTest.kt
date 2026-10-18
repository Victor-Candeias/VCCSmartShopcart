package pt.vcc.smartshopcart.data.reports

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pt.vcc.smartshopcart.core.database.ShoppingDatabase
import pt.vcc.smartshopcart.core.database.entity.ExpenseCategoryEntity
import pt.vcc.smartshopcart.core.database.entity.ExpenseEntity
import pt.vcc.smartshopcart.core.database.entity.ExpenseItemEntity
import pt.vcc.smartshopcart.core.database.entity.ExpenseProductEntity
import pt.vcc.smartshopcart.core.database.entity.ProductEntity
import pt.vcc.smartshopcart.core.database.entity.ShoppingSessionEntity
import pt.vcc.smartshopcart.core.database.entity.ShoppingSessionItemEntity
import pt.vcc.smartshopcart.core.database.entity.StoreEntity
import pt.vcc.smartshopcart.data.repository.RoomExpenseRepository
import pt.vcc.smartshopcart.data.repository.RoomShoppingSessionRepository
import pt.vcc.smartshopcart.domain.model.MeasureUnit
import pt.vcc.smartshopcart.domain.model.PriceSource
import pt.vcc.smartshopcart.domain.model.ProductSource
import pt.vcc.smartshopcart.domain.model.SessionMode
import pt.vcc.smartshopcart.domain.model.SessionStatus
import pt.vcc.smartshopcart.domain.model.expense.ExpenseDraft
import pt.vcc.smartshopcart.domain.model.expense.ExpenseDraftItem
import pt.vcc.smartshopcart.domain.reports.GenerateReportUseCase
import pt.vcc.smartshopcart.domain.reports.ReportFilter
import pt.vcc.smartshopcart.domain.reports.ReportMode
import pt.vcc.smartshopcart.domain.reports.ReportPeriodPreset
import pt.vcc.smartshopcart.domain.reports.forPreset
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

/** Cenários de dados do §15 sobre a base de dados real (Room em memória). */
@RunWith(RobolectricTestRunner::class)
class ReportsRepositoryTest {

    private val zone = ZoneId.of("Europe/Lisbon")
    private val now = at("2026-04-15", "12:00")
    private val counter = AtomicInteger()

    private lateinit var db: ShoppingDatabase
    private lateinit var generate: GenerateReportUseCase
    private lateinit var expenses: RoomExpenseRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ShoppingDatabase::class.java).allowMainThreadQueries().build()
        generate = GenerateReportUseCase(ReportsRepositoryImpl(db), { now }, { zone })
        expenses = RoomExpenseRepository(db, { "id-${counter.incrementAndGet()}" }, { now })
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `despesa com duas linhas conta uma transacao`() = runTest {
        seedCatalog()
        saveExpense("2026-04-10", "09:00", "Pastelaria", "ep-cafe" to 85, "ep-bolo" to 120)

        val report = generate(april())

        assertEquals(205L, report.summary.totalCents)
        assertEquals(1, report.summary.transactionCount)
        assertEquals(2, report.summary.itemCount)
        assertEquals(205L, report.summary.averageTransactionCents)
        assertTrue(report.rows.all { it.mode == ReportMode.EXPENSE })
    }

    @Test
    fun `listas nao concluidas e sessoes canceladas ficam de fora`() = runTest {
        session("ativa", SessionMode.PLANNED, SessionStatus.ACTIVE, completedAt = null, 300)
        session("cancelada", SessionMode.CALCULATOR, SessionStatus.CANCELLED, completedAt = at("2026-04-02", "10:00"), 400)
        session("concluida", SessionMode.PLANNED, SessionStatus.COMPLETED, completedAt = at("2026-04-03", "10:00"), 500)

        val report = generate(april())

        assertEquals(500L, report.summary.totalCents)
        assertEquals(listOf("concluida"), report.rows.map { it.transactionId }.distinct())
        assertEquals(ReportMode.PLANNED_LIST, report.rows.single().mode)
    }

    @Test
    fun `sessao e despesa com o mesmo id contam como duas transacoes`() = runTest {
        session("x", SessionMode.CALCULATOR, SessionStatus.COMPLETED, at("2026-04-05", "18:00"), 1000)
        db.expenseDao().upsertExpense(ExpenseEntity("x", null, at("2026-04-06", "08:00"), 85, null, now, now))
        db.expenseDao().insertItems(listOf(ExpenseItemEntity("xi", "x", null, "Café", 1, 85, 85, 0)))

        val report = generate(april())

        assertEquals(2, report.summary.transactionCount)
        assertEquals(1085L, report.summary.totalCents)
        assertEquals(543L, report.summary.averageTransactionCents)
    }

    @Test
    fun `filtro por categoria mantem opcoes do periodo e agrupa estabelecimentos pelo nome`() = runTest {
        seedCatalog()
        db.storeDao().insert(StoreEntity("st1", "Continente", 1))
        session("s1", SessionMode.CALCULATOR, SessionStatus.COMPLETED, at("2026-04-04", "11:00"), 900, storeId = "st1")
        saveExpense("2026-04-07", "08:00", "continente ", "ep-cafe" to 85)
        saveExpense("2026-04-08", "08:00", "Padaria", "ep-pao" to 150)

        val all = generate(april())
        val cafeId = all.options.categories.first { it.label == "Café" }.id
        val filtered = generate(april().copy(categoryIds = setOf(cafeId)))

        assertEquals(85L, filtered.summary.totalCents)
        assertEquals(all.options, filtered.options)
        assertEquals(3, all.options.categories.size)
        val continente = all.options.establishments.single { it.label.equals("Continente", ignoreCase = true) }
        assertEquals(985L, continente.amountCents)
        assertEquals(
            900L,
            generate(april().copy(categoryIds = setOf(ReportFilter.NO_CATEGORY_ID))).summary.totalCents,
        )
    }

    @Test
    fun `limite do dia respeita a mudanca de hora em Lisboa`() = runTest {
        seedCatalog()
        saveExpense("2026-03-28", "23:59", null, "ep-cafe" to 1)
        saveExpense("2026-03-29", "00:30", null, "ep-cafe" to 10)
        saveExpense("2026-03-29", "23:30", null, "ep-cafe" to 100)
        saveExpense("2026-03-30", "00:15", null, "ep-cafe" to 1000)

        val day = LocalDate.parse("2026-03-29")
        val report = generate(ReportFilter(day, day), compareWithPrevious = false)

        assertEquals(110L, report.summary.totalCents)
    }

    @Test
    fun `snapshots mantem categoria e nome apos alterar o catalogo`() = runTest {
        seedCatalog()
        saveExpense("2026-04-09", "08:00", null, "ep-cafe" to 85)
        db.productDao().upsert(ProductEntity("p1", "560", "Leite", null, null, "Laticínios", null, ProductSource.MANUAL, 1))
        session("s1", SessionMode.CALCULATOR, SessionStatus.ACTIVE, null, 89, productId = "p1")
        RoomShoppingSessionRepository(db).completeSession("s1", at("2026-04-09", "10:00")) { "h-${counter.incrementAndGet()}" }

        val dao = db.expenseDao()
        dao.insertCategoriesIgnoringExisting(listOf(ExpenseCategoryEntity("c-outra", "Outra", null, 9, true)))
        dao.upsertProduct(dao.getProduct("ep-cafe")!!.copy(categoryId = "c-outra"))
        db.openHelper.writableDatabase.execSQL("UPDATE expense_categories SET name = 'Renomeada' WHERE id = 'c-cafe'")
        db.productDao().upsert(ProductEntity("p1", "560", "Leite Magro", null, null, "Bebidas", null, ProductSource.MANUAL, 2))

        val categories = generate(april()).rows.associate { it.mode to it.categoryName }

        assertEquals("Café", categories[ReportMode.EXPENSE])
        assertEquals("Laticínios", categories[ReportMode.CALCULATOR])
    }

    @Test
    fun `periodo vazio devolve zeros`() = runTest {
        val report = generate(april())

        assertTrue(report.isEmpty)
        assertEquals(0L, report.summary.totalCents)
        assertEquals(0, report.summary.transactionCount)
        assertEquals(0L, report.summary.averageTransactionCents)
        assertFalse(report.dataQuality.hasIssues)
    }

    @Test
    fun `orcamento vs realizado e avisos de dados incompletos`() = runTest {
        session("b", SessionMode.PLANNED, SessionStatus.COMPLETED, at("2026-04-02", "10:00"), 1200, budget = 1000)
        session("vazia", SessionMode.CALCULATOR, SessionStatus.COMPLETED, at("2026-04-02", "11:00"), total = null)
        db.openHelper.writableDatabase.execSQL("UPDATE shopping_sessions SET finalTotalCents = 999 WHERE id = 'b'")

        val report = generate(april())

        val budget = report.budgets.single()
        assertEquals(1200L, budget.spentCents)
        assertTrue(budget.overBudget)
        assertEquals(1, report.dataQuality.transactionsWithoutItems)
        assertEquals(1, report.dataQuality.totalMismatches)
        assertTrue(generate(april().copy(modes = setOf(ReportMode.EXPENSE))).budgets.isEmpty())
    }

    @Test
    fun `este mes compara com os mesmos dias do mes anterior`() = runTest {
        session("marco", SessionMode.CALCULATOR, SessionStatus.COMPLETED, at("2026-03-10", "10:00"), 400)
        session("marco-fora", SessionMode.CALCULATOR, SessionStatus.COMPLETED, at("2026-03-20", "10:00"), 999)
        session("abril", SessionMode.CALCULATOR, SessionStatus.COMPLETED, at("2026-04-10", "10:00"), 500)

        val comparison = generate(ReportFilter.forPreset(ReportPeriodPreset.THIS_MONTH, LocalDate.parse("2026-04-15"))).comparison!!

        assertEquals(LocalDate.parse("2026-03-01"), comparison.previousStartDate)
        assertEquals(LocalDate.parse("2026-03-15"), comparison.previousEndDateInclusive)
        assertEquals(400L, comparison.previousTotalCents)
        assertEquals(25.0, comparison.differencePercent!!, 0.001)
        assertTrue(comparison.currentPeriodIncomplete)
        assertNull(generate(april(), compareWithPrevious = false).comparison)
    }

    private fun april() = ReportFilter(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30"))

    private fun at(date: String, time: String): Long =
        LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli()

    private suspend fun seedCatalog() {
        val dao = db.expenseDao()
        dao.insertCategoriesIgnoringExisting(
            listOf(
                ExpenseCategoryEntity("c-cafe", "Café", null, 0, true),
                ExpenseCategoryEntity("c-pad", "Padaria", null, 1, true),
            ),
        )
        listOf(Triple("ep-cafe", "c-cafe", "Café"), Triple("ep-bolo", "c-pad", "Bolo"), Triple("ep-pao", "c-pad", "Pão"))
            .forEachIndexed { index, (id, category, name) ->
                dao.upsertProduct(
                    ExpenseProductEntity(
                        id = id,
                        categoryId = category,
                        name = name,
                        icon = null,
                        defaultPriceCents = null,
                        isFavorite = false,
                        isActive = true,
                        sortOrder = index,
                    ),
                )
            }
    }

    private suspend fun saveExpense(date: String, time: String, place: String?, vararg items: Pair<String, Long>): String =
        expenses.saveExpense(
            ExpenseDraft(
                establishmentName = place,
                occurredAt = at(date, time),
                items = items.map { (productId, price) ->
                    ExpenseDraftItem(productId, db.expenseDao().getProduct(productId)!!.name, 1, price)
                },
            ),
            null,
        )

    private suspend fun session(
        id: String,
        mode: SessionMode,
        status: SessionStatus,
        completedAt: Long?,
        total: Long?,
        budget: Long? = null,
        storeId: String? = null,
        productId: String? = null,
    ) {
        val dao = db.shoppingSessionDao()
        dao.insertSession(
            ShoppingSessionEntity(
                id = id,
                shoppingListId = null,
                mode = mode,
                status = status,
                storeId = storeId,
                budgetCents = budget,
                startedAt = completedAt ?: now,
                completedAt = completedAt,
                finalTotalCents = if (status == SessionStatus.COMPLETED) total ?: 0 else null,
            ),
        )
        if (total != null) {
            dao.insertItem(
                ShoppingSessionItemEntity(
                    id = "$id-item",
                    sessionId = id,
                    shoppingListItemId = null,
                    productId = productId,
                    name = null,
                    barcode = null,
                    quantity = "1",
                    unit = MeasureUnit.UN,
                    unitPriceCents = total,
                    discountCents = 0,
                    totalCents = total,
                    priceSource = PriceSource.MANUAL,
                    addedAt = completedAt ?: now,
                ),
            )
        }
    }
}

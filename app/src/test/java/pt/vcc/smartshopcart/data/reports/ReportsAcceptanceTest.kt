package pt.vcc.smartshopcart.data.reports

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pt.vcc.smartshopcart.core.database.ShoppingDatabase
import pt.vcc.smartshopcart.core.database.entity.ExpenseEntity
import pt.vcc.smartshopcart.core.database.entity.ExpenseItemEntity
import pt.vcc.smartshopcart.core.database.entity.ShoppingSessionEntity
import pt.vcc.smartshopcart.core.database.entity.ShoppingSessionItemEntity
import pt.vcc.smartshopcart.data.reports.export.CsvReportExporter
import pt.vcc.smartshopcart.data.reports.export.ExportCoordinator
import pt.vcc.smartshopcart.data.reports.export.ExportFormat
import pt.vcc.smartshopcart.data.reports.export.ExportGranularity
import pt.vcc.smartshopcart.data.reports.export.ExportOptions
import pt.vcc.smartshopcart.data.reports.export.JsonReportExporter
import pt.vcc.smartshopcart.data.reports.export.PdfReportExporter
import pt.vcc.smartshopcart.data.reports.export.ReportExportDto
import pt.vcc.smartshopcart.data.reports.export.centsPt
import pt.vcc.smartshopcart.data.reports.export.pdf.PdfLayoutEngine
import pt.vcc.smartshopcart.data.reports.export.pdf.PdfOp
import pt.vcc.smartshopcart.data.reports.export.pdf.PdfReportContentBuilder
import pt.vcc.smartshopcart.domain.model.MeasureUnit
import pt.vcc.smartshopcart.domain.model.PriceSource
import pt.vcc.smartshopcart.domain.model.SessionMode
import pt.vcc.smartshopcart.domain.model.SessionStatus
import pt.vcc.smartshopcart.domain.reports.GenerateReportUseCase
import pt.vcc.smartshopcart.domain.reports.ReportData
import pt.vcc.smartshopcart.domain.reports.ReportFilter
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Critérios de aceitação do MVP (§17) sobre a base de dados real (Room em memória), sem rede:
 * o total do relatório coincide com a BD e CSV, JSON e PDF apresentam os mesmos totais.
 */
@RunWith(RobolectricTestRunner::class)
class ReportsAcceptanceTest {

    private val zone = ZoneId.of("Europe/Lisbon")
    private val now = at("2026-04-20", "12:00")

    private lateinit var context: Context
    private lateinit var db: ShoppingDatabase
    private lateinit var generate: GenerateReportUseCase
    private lateinit var exports: ExportCoordinator

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ShoppingDatabase::class.java).allowMainThreadQueries().build()
        generate = GenerateReportUseCase(ReportsRepositoryImpl(db), { now }, { zone })
        exports = ExportCoordinator(context, setOf(CsvReportExporter(), JsonReportExporter(), PdfReportExporter()))
    }

    @After
    fun tearDown() {
        exports.clearShareFiles()
        db.close()
    }

    @Test
    fun `total do relatorio coincide com a soma das transacoes concluidas`() = runTest {
        seed()

        val report = generate(april())

        val sessions = scalar("SELECT COALESCE(SUM(finalTotalCents), 0) FROM shopping_sessions WHERE status = 'COMPLETED'")
        val expenses = scalar("SELECT COALESCE(SUM(totalCents), 0) FROM expenses")
        assertEquals(sessions + expenses, report.summary.totalCents)
        assertEquals(4, report.summary.transactionCount)
    }

    @Test
    fun `csv json e pdf apresentam o mesmo total e numero de transacoes`() = runTest {
        seed()
        val report = generate(april())
        val total = report.summary.totalCents

        val csvItems = csv(report, ExportGranularity.ITEMS)
        assertEquals(report.rows.size, csvItems.size)
        assertEquals(total, csvItems.sumOf { cents(it[10]) })
        assertEquals(report.summary.transactionCount, csvItems.map { it[2] to it[11] }.distinct().size)
        assertTrue(csvItems.any { it[5] == "\"Maçãs Golden — São Tomé\"" })

        val csvTransactions = csv(report, ExportGranularity.TRANSACTIONS)
        assertEquals(report.summary.transactionCount, csvTransactions.size)
        assertEquals(total, csvTransactions.sumOf { cents(it[5]) })

        val json = json(report, ExportGranularity.ITEMS)
        assertEquals(total, json.summary.totalCents)
        assertEquals(total, json.items!!.sumOf { it.totalCents })
        assertEquals(report.summary.transactionCount, json.summary.transactionCount)
        assertEquals(total, json(report, ExportGranularity.TRANSACTIONS).transactions!!.sumOf { it.totalCents })

        val content = PdfReportContentBuilder.build(report)
        assertEquals(PdfReportContentBuilder.money(total), content.kpis.first().value)
        assertEquals(report.summary.transactionCount.toString(), content.kpis[1].value)
        assertEquals(total, content.table.rows.sumOf { cents(it.last()) })
        val pdfTexts = PdfLayoutEngine({ text, style -> text.length * style.size * 0.5f }).layout(content)
            .flatMap { page -> page.ops.filterIsInstance<PdfOp.Text>().map { it.text } }
        assertTrue(pdfTexts.any { it.contains("Maçãs") })
    }

    @Test
    fun `relatorio vazio exporta ficheiros validos com total zero`() = runTest {
        val report = generate(april())

        assertTrue(report.isEmpty)
        assertTrue(csv(report, ExportGranularity.ITEMS).isEmpty())
        val json = json(report, ExportGranularity.ITEMS)
        assertEquals(0L, json.summary.totalCents)
        assertTrue(json.items!!.isEmpty())
        val pages = PdfLayoutEngine({ text, style -> text.length * style.size * 0.5f }).layout(PdfReportContentBuilder.build(report))
        assertEquals(1, pages.size)
    }

    @Test
    fun `falha de escrita propaga erro e nao deixa ficheiro`() = runTest {
        seed()
        val target = File(context.filesDir, "nao-existe/relatorio.csv")

        try {
            exports.writeToUri(Uri.fromFile(target), generate(april()), ExportFormat.CSV, ExportOptions())
            fail("devia falhar")
        } catch (expected: IOException) {
            assertFalse(target.exists())
        }
    }

    private suspend fun csv(report: ReportData, granularity: ExportGranularity): List<List<String>> {
        val file = exports.writeShareFile(report, ExportFormat.CSV, ExportOptions(granularity), "relatorio")
        return file.readText(Charsets.UTF_8).removePrefix("\uFEFF").split("\r\n").filter { it.isNotEmpty() }.drop(1).map { it.split(';') }
    }

    private suspend fun json(report: ReportData, granularity: ExportGranularity): ReportExportDto {
        val file = exports.writeShareFile(report, ExportFormat.JSON, ExportOptions(granularity), "relatorio")
        return JsonReportExporter.json.decodeFromString(ReportExportDto.serializer(), file.readText())
    }

    private fun cents(value: String): Long = BigDecimal(value.replace(',', '.')).movePointRight(2).longValueExact()

    private fun scalar(sql: String): Long = db.openHelper.readableDatabase.query(sql).use { it.moveToFirst(); it.getLong(0) }

    private fun april() = ReportFilter(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30"))

    private fun at(date: String, time: String): Long = LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli()

    private suspend fun seed() {
        session("calc", SessionMode.CALCULATOR, SessionStatus.COMPLETED, at("2026-04-03", "18:30"), "Maçãs Golden — São Tomé" to 349, "Pão" to 120)
        session("lista", SessionMode.PLANNED, SessionStatus.COMPLETED, at("2026-04-12", "10:00"), "Leite" to 89)
        session("ativa", SessionMode.PLANNED, SessionStatus.ACTIVE, null, "Arroz" to 999)
        session("cancelada", SessionMode.CALCULATOR, SessionStatus.CANCELLED, at("2026-04-05", "09:00"), "Água" to 555)
        expense("e1", at("2026-04-10", "08:15"), "Café" to 85, "Bola de Berlim" to 150)
        expense("e2", at("2026-04-18", "13:00"), "Almoço" to 1050)
    }

    private suspend fun session(id: String, mode: SessionMode, status: SessionStatus, completedAt: Long?, vararg items: Pair<String, Long>) {
        val dao = db.shoppingSessionDao()
        dao.insertSession(
            ShoppingSessionEntity(
                id = id,
                shoppingListId = null,
                mode = mode,
                status = status,
                storeId = null,
                budgetCents = null,
                startedAt = completedAt ?: now,
                completedAt = completedAt,
                finalTotalCents = if (status == SessionStatus.COMPLETED) items.sumOf { it.second } else null,
            ),
        )
        items.forEachIndexed { index, (name, total) ->
            dao.insertItem(
                ShoppingSessionItemEntity(
                    id = "$id-$index",
                    sessionId = id,
                    shoppingListItemId = null,
                    productId = null,
                    name = name,
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

    private suspend fun expense(id: String, occurredAt: Long, vararg items: Pair<String, Long>) {
        val dao = db.expenseDao()
        dao.upsertExpense(ExpenseEntity(id, null, occurredAt, items.sumOf { it.second }, null, now, now))
        dao.insertItems(items.mapIndexed { index, (name, total) -> ExpenseItemEntity("$id-$index", id, null, name, 1, total, total, 0) })
    }
}

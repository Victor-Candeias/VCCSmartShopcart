package pt.vcc.smartshopcart.domain.reports

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ReportSeriesTest {

    private val zone = ZoneId.of("Europe/Lisbon")

    @Test
    fun `linha temporal diaria preenche dias sem movimentos com zero`() {
        val rows = listOf(row("t1", "2026-04-01T10:00", 100), row("t2", "2026-04-03T23:30", 50), row("t2", "2026-04-03T23:30", 25))

        val points = ReportSeries.timeline(rows, date("2026-04-01"), date("2026-04-04"), zone, TimeGranularity.DAY)

        assertEquals(listOf(100L, 0L, 75L, 0L), points.map { it.amountCents })
        assertEquals(listOf(1, 0, 1, 0), points.map { it.transactionCount })
    }

    @Test
    fun `linha temporal semanal comeca a segunda e mensal no dia 1`() {
        val rows = listOf(row("t1", "2026-04-05T10:00", 100), row("t2", "2026-04-06T10:00", 30))

        val weeks = ReportSeries.timeline(rows, date("2026-04-01"), date("2026-04-14"), zone, TimeGranularity.WEEK)
        val months = ReportSeries.timeline(rows, date("2026-02-15"), date("2026-04-14"), zone, TimeGranularity.MONTH)

        assertEquals(listOf(date("2026-03-30"), date("2026-04-06"), date("2026-04-13")), weeks.map { it.start })
        assertEquals(listOf(100L, 30L, 0L), weeks.map { it.amountCents })
        assertEquals(listOf(0L, 0L, 130L), months.map { it.amountCents })
    }

    @Test
    fun `granularidade por omissao depende da duracao`() {
        assertEquals(TimeGranularity.DAY, ReportSeries.defaultGranularity(date("2026-04-01"), date("2026-04-30")))
        assertEquals(TimeGranularity.WEEK, ReportSeries.defaultGranularity(date("2026-01-01"), date("2026-04-30")))
        assertEquals(TimeGranularity.MONTH, ReportSeries.defaultGranularity(date("2026-01-01"), date("2026-12-31")))
    }

    @Test
    fun `categorias ordenadas com quota e colapso em Outros`() {
        val rows = (1..8).map { row("t$it", "2026-04-01T10:00", it * 10L, category = "C$it") } +
            row("t9", "2026-04-01T10:00", 5, category = null)

        val categories = ReportSeries.byCategory(rows)
        val collapsed = ReportSeries.collapse(categories, maxSlices = 4)

        assertEquals("C8", categories.first().label)
        assertEquals(ReportLabels.NO_CATEGORY, categories.last().label)
        assertEquals(1.0, categories.sumOf { it.share }, 1e-9)
        assertEquals(listOf("C8", "C7", "C6", ReportLabels.OTHERS), collapsed.map { it.label })
        assertEquals(rows.sumOf { it.totalCents }, collapsed.sumOf { it.amountCents })
    }

    @Test
    fun `produtos somam quantidade e contam frequencia por transacao`() {
        val rows = listOf(
            row("t1", "2026-04-01T10:00", 100, product = "Leite", quantity = "2"),
            row("t1", "2026-04-01T10:00", 50, product = "Leite", quantity = "1"),
            row("t2", "2026-04-02T10:00", 50, product = "leite ", quantity = "1"),
        )

        val product = ReportSeries.byProduct(rows).single()

        assertEquals(200L, product.amountCents)
        assertEquals(2, product.frequency)
        assertEquals(BigDecimal("4"), product.quantity)
    }

    @Test
    fun `transacoes agrupam por modo e id`() {
        val rows = listOf(
            row("x", "2026-04-01T10:00", 100, mode = ReportMode.EXPENSE),
            row("x", "2026-04-02T10:00", 70, mode = ReportMode.CALCULATOR),
        )

        val transactions = ReportSeries.transactions(rows)

        assertEquals(listOf(ReportMode.CALCULATOR, ReportMode.EXPENSE), transactions.map { it.mode })
        assertEquals(2, GenerateReportUseCase.summarize(rows).transactionCount)
        assertEquals(85L, GenerateReportUseCase.summarize(rows).averageTransactionCents)
    }

    @Test
    fun `periodo anterior equivalente`() {
        val today = date("2026-04-15")
        fun previous(filter: ReportFilter) = filter.previousPeriod()

        assertEquals(date("2026-03-01") to date("2026-03-31"), previous(ReportFilter.forPreset(ReportPeriodPreset.PREVIOUS_MONTH, date("2026-05-02"))))
        assertEquals(date("2026-03-01") to date("2026-03-15"), previous(ReportFilter.forPreset(ReportPeriodPreset.THIS_MONTH, today)))
        assertEquals(date("2025-01-01") to date("2025-04-15"), previous(ReportFilter.forPreset(ReportPeriodPreset.THIS_YEAR, today)))
        assertEquals(date("2026-04-02") to date("2026-04-08"), previous(ReportFilter.forPreset(ReportPeriodPreset.LAST_7_DAYS, today)))
        assertEquals(date("2026-04-14") to date("2026-04-14"), previous(ReportFilter.forPreset(ReportPeriodPreset.TODAY, today)))
        assertEquals(date("2025-11-01") to date("2026-01-31"), previous(ReportFilter(date("2026-02-01"), date("2026-04-30"))))
        assertEquals(date("2026-02-28") to date("2026-03-31"), previous(ReportFilter(date("2026-04-01"), date("2026-05-02"))))
    }

    @Test
    fun `intervalo epoch cobre o dia local de 23 horas`() {
        val day = date("2026-03-29")
        val (from, until) = ReportFilter(day, day).toEpochRange(zone)

        assertEquals(23 * 3_600_000L, until - from)
    }

    private fun date(text: String) = LocalDate.parse(text)

    private fun row(
        transaction: String,
        at: String,
        total: Long,
        category: String? = "Mercearia",
        product: String = "Artigo",
        quantity: String = "1",
        mode: ReportMode = ReportMode.EXPENSE,
    ) = ReportRow(
        transactionId = transaction,
        itemId = "$transaction-$total-$product",
        occurredAt = LocalDateTime.parse(at).atZone(zone).toInstant(),
        mode = mode,
        establishmentId = null,
        establishmentName = null,
        categoryId = category?.let { "c:$it" },
        categoryName = category,
        productId = null,
        productName = product,
        quantity = BigDecimal(quantity),
        unit = "un",
        unitPriceCents = total,
        discountCents = 0,
        totalCents = total,
    )
}

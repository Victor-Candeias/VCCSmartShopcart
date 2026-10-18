package pt.vcc.smartshopcart.data.reports.export

import pt.vcc.smartshopcart.domain.reports.GenerateReportUseCase
import pt.vcc.smartshopcart.domain.reports.ReportData
import pt.vcc.smartshopcart.domain.reports.ReportFilter
import pt.vcc.smartshopcart.domain.reports.ReportMode
import pt.vcc.smartshopcart.domain.reports.ReportPeriodPreset
import pt.vcc.smartshopcart.domain.reports.ReportRow
import pt.vcc.smartshopcart.domain.reports.forPreset
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Relatórios de teste partilhados pelos exportadores. */
object ReportFixtures {
    val zone: ZoneId = ZoneId.of("Europe/Lisbon")

    fun row(
        transactionId: String,
        at: LocalDateTime,
        product: String,
        totalCents: Long,
        mode: ReportMode = ReportMode.EXPENSE,
        establishment: String? = "Pastelaria Sé",
        category: String? = "Café e pastelaria",
        quantity: String = "1",
        unit: String = "un",
        discountCents: Long = 0,
    ) = ReportRow(
        transactionId = transactionId,
        itemId = "$transactionId-$product",
        occurredAt = at.atZone(zone).toInstant(),
        mode = mode,
        establishmentId = establishment?.let { "place:${it.lowercase()}" },
        establishmentName = establishment,
        categoryId = category?.let { "cat-${it.lowercase()}" },
        categoryName = category,
        productId = null,
        productName = product,
        quantity = BigDecimal(quantity),
        unit = unit,
        unitPriceCents = totalCents + discountCents,
        discountCents = discountCents,
        totalCents = totalCents,
    )

    fun report(rows: List<ReportRow>): ReportData = ReportData(
        filter = ReportFilter.forPreset(ReportPeriodPreset.THIS_MONTH, LocalDate.of(2026, 4, 15)),
        rows = rows,
        summary = GenerateReportUseCase.summarize(rows),
        generatedAt = LocalDateTime.of(2026, 4, 15, 12, 0).atZone(zone).toInstant(),
        zoneId = zone,
    )

    /** Café + bola de Berlim na mesma despesa (exemplo do §10). */
    fun coffee(): ReportData = report(
        listOf(
            row("abc", LocalDateTime.of(2026, 4, 10, 9, 30), "Café", 85),
            row("abc", LocalDateTime.of(2026, 4, 10, 9, 30), "Bola de Berlim", 150),
        ),
    )

    /** Inclui nomes perigosos para CSV e um artigo a granel com desconto. */
    fun tricky(): ReportData = report(
        listOf(
            row("t1", LocalDateTime.of(2026, 4, 2, 18, 5), "=HYPERLINK(\"x\")", 199, ReportMode.CALCULATOR, "Loja; \"A\"", null),
            row(
                "t1", LocalDateTime.of(2026, 4, 2, 18, 5), "Maçãs", 123, ReportMode.CALCULATOR, "Loja; \"A\"", "Fruta",
                quantity = "1.250", unit = "kg", discountCents = 20,
            ),
            row("t2", LocalDateTime.of(2026, 4, 3, 8, 0), "-Pão", 45, ReportMode.PLANNED_LIST, null, "Padaria"),
        ),
    )
}
